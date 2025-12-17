package com.example.qarag.qa;

import com.example.qarag.api.dto.QaResponse;
import com.example.qarag.config.RagProperties;
import com.example.qarag.domain.Chunk;
import com.example.qarag.utils.MmrUtils;
import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import com.pgvector.PGvector;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QAServiceImpl implements QAService {

    private final EmbeddingModel embeddingModel;
    private final ChatModel chatLanguageModel;
    private final RagProperties ragProperties;
    private final JdbcClient jdbcClient; // Use JdbcClient for complex queries
    private final Executor searchExecutor; // Injected Custom Executor
    private final JiebaSegmenter jiebaSegmenter = new JiebaSegmenter();
    private final Set<String> stopWords = new HashSet<>();

    @PostConstruct
    public void loadStopWords() {
        try {
            ClassPathResource resource = new ClassPathResource("stopwords.txt");
            if (resource.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.trim().isEmpty()) {
                            stopWords.add(line.trim());
                        }
                    }
                }
                log.info("Loaded {} stop words.", stopWords.size());
            } else {
                log.warn("stopwords.txt not found in resources.");
            }
        } catch (Exception e) {
            log.error("Failed to load stop words", e);
        }
    }

    private static final String PROMPT_TEMPLATE = """
            你是一个知识库助手。根据以下文档片段回答用户的问题。不要编造答案。如果无法从片段中找到明确答案，请说“未找到明确答案”。

            用户问题：%s

            文档片段：
            %s

            请基于上面片段回答，并在答案末尾列出每个引用段落对应的来源标识（例如：文件名:页:chunkIndex）。
            """;

    @Override
    public QaResponse answer(String question) {
        // For a global answer without specific document filters, call hybridSearch with an empty documentIds list.
        // The hybridSearch method will return an empty list if no document IDs are provided, indicating no specific context.
        List<Chunk> relevantChunks = hybridSearch(question, ragProperties.retrieval().topK(), Collections.emptyList());

        // If no relevant chunks are found, we can still attempt to answer using LLM without RAG context
        // Or return a specific message indicating no context was found.
        String chunksContext = relevantChunks.isEmpty() ? 
            "" : relevantChunks.stream()
                .map(Chunk::getContent)
                .collect(Collectors.joining("\n---\n"));

        String prompt = String.format(PROMPT_TEMPLATE, question, chunksContext);

        // Call the LLM to generate an answer
        String answer = chatLanguageModel.chat(prompt);

        return new QaResponse(answer, relevantChunks);
    }

    /**
     * Executes hybrid search: Vector Search + Keyword Search -> RRF Fusion
     */
    @Override
    public List<Chunk> hybridSearch(String question, int finalK, List<UUID> documentIds) {
        long startTime = System.currentTimeMillis();
        log.info("Starting hybrid search for question: '{}' with {} document(s) and finalK: {}", question, documentIds.size(), finalK);

        if (documentIds.isEmpty()) {
            log.info("No document IDs provided for hybrid search, returning empty list as no specific context is given.");
            return Collections.emptyList();
        }

        int searchK = ragProperties.retrieval().topK(); // Fetch top from each source
        int rrfK = 60;    // RRF constant

        String documentIdsClause = documentIds.stream()
                .map(uuid -> "'" + uuid.toString() + "'")
                .collect(Collectors.joining(", "));

        // 1. Prepare Vector Search Task
        CompletableFuture<List<Chunk>> vectorSearchFuture = CompletableFuture.supplyAsync(() -> {
            long vectorSearchStart = System.currentTimeMillis();
            TextSegment questionSegment = TextSegment.from(question);
            float[] queryEmbedding = embeddingModel.embedAll(List.of(questionSegment)).content().getFirst().vector();

            String vectorSql = """
                            SELECT id, document_id, content, content_vector, chunk_index, source_meta, chunker_name, content_keywords, created_at\s
                            FROM chunks\s
                            WHERE document_id IN (%s)
                            ORDER BY content_vector <=> ?\s
                            LIMIT ?
                           \s""".formatted(documentIdsClause);
            
            // MMR Parameters
            int fetchK = searchK * 5; // Fetch more candidates for diversity re-ranking
            double mmrLambda = 0.5;   // Balance between relevance and diversity

            log.info("Vector Search SQL: {}", vectorSql);
            log.info("Vector Search Params: embedding={}, fetchLimit={}", Arrays.toString(queryEmbedding), fetchK);
            
            List<Chunk> initialResults = jdbcClient.sql(vectorSql)
                    .params(new PGvector(queryEmbedding), fetchK)
                    .query(new ChunkRowMapper())
                    .list();
            
            log.info("Vector search fetched {} candidates in {} ms", initialResults.size(), System.currentTimeMillis() - vectorSearchStart);

            // Apply MMR Re-ranking
            long mmrStart = System.currentTimeMillis();
            List<Chunk> finalResults = MmrUtils.applyMmr(initialResults, queryEmbedding, searchK, mmrLambda);
            log.info("MMR processing took {} ms. Reduced {} candidates to {}.", System.currentTimeMillis() - mmrStart, initialResults.size(), finalResults.size());

            // Clear vectors to save memory as they are not needed for subsequent steps
            finalResults.forEach(c -> c.setContentVector(null));

            return finalResults;
        }, searchExecutor);

        // 2. Prepare Keyword Search Task
        CompletableFuture<List<Chunk>> keywordSearchFuture = CompletableFuture.supplyAsync(() -> {
            long keywordSearchStart = System.currentTimeMillis();
            List<SegToken> tokens = jiebaSegmenter.process(question, JiebaSegmenter.SegMode.SEARCH);
            
            // Filter stop words
            String segmentedQuery = tokens.stream()
                    .map(i -> i.word)
                    .filter(word -> !stopWords.contains(word))
                    .collect(Collectors.joining(" "));

            if (segmentedQuery.isBlank()) {
                // If all words are filtered out (unlikely but possible), fall back to original or handle gracefully
                log.warn("All keywords filtered out for query: '{}'. Falling back to original query.", question);
                segmentedQuery = tokens.stream().map(i -> i.word).collect(Collectors.joining(" "));
            }

            log.info("Keyword search segmented query: '{}'", segmentedQuery);

            String keywordSql = """
                            SELECT id, document_id, content, NULL as content_vector, chunk_index, source_meta, chunker_name, content_keywords, created_at\s
                            FROM chunks\s
                            WHERE document_id IN (%s)
                            AND content_search @@ websearch_to_tsquery('simple', ?)
                            ORDER BY ts_rank(content_search, websearch_to_tsquery('simple', ?)) DESC
                            LIMIT ?
                           \s""".formatted(documentIdsClause);
            log.info("Keyword Search SQL: {}", keywordSql);
            log.info("Keyword Search Params: query='{}', limit={}", segmentedQuery, searchK);
            List<Chunk> results = jdbcClient.sql(keywordSql)
                    .params(segmentedQuery, segmentedQuery, searchK)
                    .query(new ChunkRowMapper())
                    .list();
            log.info("Keyword search found {} results in {} ms", results.size(), System.currentTimeMillis() - keywordSearchStart);
            return results;
        }, searchExecutor);

        // 3. Wait for both and Retrieve Results
        List<Chunk> vectorResults;
        List<Chunk> keywordResults;
        try {
            CompletableFuture.allOf(vectorSearchFuture, keywordSearchFuture).join();
            vectorResults = vectorSearchFuture.get();
            keywordResults = keywordSearchFuture.get();
        } catch (Exception e) {
            log.error("Error during hybrid search execution", e);
            throw new RuntimeException("Hybrid search failed", e);
        }

        // 4. RRF Fusion
        Map<UUID, Double> scores = new HashMap<>();
        Map<UUID, Chunk> chunkMap = new HashMap<>();

        // Process Vector Results
        for (int i = 0; i < vectorResults.size(); i++) {
            Chunk chunk = vectorResults.get(i);
            chunkMap.putIfAbsent(chunk.getId(), chunk);
            scores.merge(chunk.getId(), 1.0 / (rrfK + i + 1), Double::sum);
        }

        // Process Keyword Results
        for (int i = 0; i < keywordResults.size(); i++) {
            Chunk chunk = keywordResults.get(i);
            chunkMap.putIfAbsent(chunk.getId(), chunk);
            scores.merge(chunk.getId(), 1.0 / (rrfK + i + 1), Double::sum);
        }

        // 5. Sort and Limit
        List<Chunk> finalResults = scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(finalK)
                .map(entry -> chunkMap.get(entry.getKey()))
                .collect(Collectors.toList());

        log.info("Hybrid search completed in {} ms. Final {} chunks after RRF fusion.", System.currentTimeMillis() - startTime, finalResults.size());
        return finalResults;
    }
    // RowMapper to map ResultSet to Chunk object
    private static class ChunkRowMapper implements RowMapper<Chunk> {
        @Override
        public Chunk mapRow(ResultSet rs, int rowNum) throws SQLException {
            Chunk chunk = new Chunk();
            chunk.setId(UUID.fromString(rs.getString("id")));
            chunk.setDocumentId(UUID.fromString(rs.getString("document_id")));
            chunk.setContent(rs.getString("content"));
            // Map content_vector for MMR calculation
            String vectorStr = rs.getString("content_vector");
            if (vectorStr != null) {
                chunk.setContentVector(new PGvector(vectorStr));
            }
            chunk.setChunkIndex(rs.getInt("chunk_index"));
            chunk.setSourceMeta(rs.getString("source_meta"));
            chunk.setChunkerName(rs.getString("chunker_name"));
            chunk.setContentKeywords(rs.getString("content_keywords"));
            chunk.setCreatedAt(rs.getObject("created_at", java.time.OffsetDateTime.class));
            return chunk;
        }
    }
}
