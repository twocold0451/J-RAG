package com.example.qarag.qa;

import com.example.qarag.api.dto.QaResponse;
import com.example.qarag.config.RagProperties;
import com.example.qarag.config.TraceContext;
import com.example.qarag.domain.Chunk;
import com.example.qarag.service.LangFuseService;
import com.example.qarag.utils.MmrUtils;
import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import com.pgvector.PGvector;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.output.Response;

@Slf4j
@Service
@RequiredArgsConstructor
public class QAServiceImpl implements QAService {

    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;
    private final RagProperties ragProperties;
    private final JdbcClient jdbcClient;
    private final Executor searchExecutor;
    private final ScoringModel scoringModel;
    private final LangFuseService langFuseService;
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
                log.info("已加载 {} 个停用词。", stopWords.size());
            } else {
                log.warn("资源中未找到 stopwords.txt。");
            }
        } catch (Exception e) {
            log.error("加载停用词失败", e);
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
        List<Chunk> relevantChunks = hybridSearch(question, Collections.emptyList());

        // If no relevant chunks are found, chunksContext will be empty
        String chunksContext = relevantChunks.isEmpty() ? 
            "" : relevantChunks.stream()
                .map(Chunk::getContent)
                .collect(Collectors.joining("\n---\n"));

        String prompt = String.format(PROMPT_TEMPLATE, question, chunksContext);

        // Call the LLM to generate an answer
        String answer = chatModel.chat(prompt);

        return new QaResponse(answer, relevantChunks);
    }

    /**
     * Executes hybrid search: Vector Search + Keyword Search -> (RRF Fusion OR Reranking)
     */
    @Override
    public List<Chunk> hybridSearch(String question, List<UUID> documentIds) {
        String traceId = TraceContext.getTraceId();
        String parentSpanId = TraceContext.getCurrentSpanId();
        String currentSpanId = UUID.randomUUID().toString();
        
        // 开启当前查询的子 Span 作用域
        TraceContext.pushSpan(currentSpanId);
        Instant startTime = Instant.now();

        boolean rerankEnabled = ragProperties.retrieval().rerank() != null && ragProperties.retrieval().rerank().enabled();
        int topK = ragProperties.retrieval().topK();
        
        // 如果开启重排序，则初始搜索数量采用 initialTopK
        int searchK = rerankEnabled ? ragProperties.retrieval().rerank().initialTopK() : topK;

        log.info("开始混合搜索问题：'{}'，模式：{}，涉及 {} 个文档，searchK：{}",
                question, (rerankEnabled ? "重排序" : "RRF 融合"), documentIds.size(), searchK);

        if (documentIds.isEmpty()) {
            TraceContext.popSpan();
            log.info("混合搜索未提供文档 ID，由于未给定特定上下文，返回空列表。");
            return Collections.emptyList();
        }

        try {
            String documentIdsClause = documentIds.stream()
                    .map(uuid -> "'" + uuid.toString() + "'")
                    .collect(Collectors.joining(", "));

            // 1. Prepare Vector Search Task
            CompletableFuture<List<Chunk>> vectorSearchFuture = CompletableFuture.supplyAsync(() -> {
                Instant vStart = Instant.now();
                long vectorSearchStart = System.currentTimeMillis();
                TextSegment questionSegment = TextSegment.from(question);
                float[] queryEmbedding = embeddingModel.embedAll(List.of(questionSegment)).content().getFirst().vector();

                String vectorSql = "SELECT id, document_id, content, content_vector, chunk_index, source_meta, chunker_name, content_keywords, created_at " +
                                   "FROM chunks " +
                                   "WHERE document_id IN (" + documentIdsClause + ") " +
                                   "ORDER BY content_vector <=> ? " +
                                   "LIMIT ?";
                
                // MMR Parameters
                int fetchK = searchK * 3; // Fetch more candidates for diversity re-ranking
                double mmrLambda = 0.5;   // Balance between relevance and diversity

                List<Chunk> initialResults = jdbcClient.sql(vectorSql)
                        .params(new PGvector(queryEmbedding), fetchK)
                        .query(new ChunkRowMapper())
                        .list();
                
                log.info("向量搜索在 {} 毫秒内获取了 {} 个候选片段", System.currentTimeMillis() - vectorSearchStart, initialResults.size());

                // 应用 MMR 重新排序
                List<Chunk> finalResults = MmrUtils.applyMmr(initialResults, queryEmbedding, searchK, mmrLambda);
                
                langFuseService.createSpan(null, traceId, currentSpanId, "Vector Search",null,
                        finalResults.stream().limit(10).collect(Collectors.toMap(
                                Chunk::getId,
                                c -> StringUtils.left(c.getContent(), 20)
                        )),
                        vStart, Instant.now());

                // Clear vectors to save memory
                finalResults.forEach(c -> c.setContentVector(null));

                return finalResults;
            }, searchExecutor);

            // 2. Prepare Keyword Search Task
            CompletableFuture<List<Chunk>> keywordSearchFuture = CompletableFuture.supplyAsync(() -> {
                Instant kStart = Instant.now();
                long keywordSearchStart = System.currentTimeMillis();
                List<SegToken> tokens = jiebaSegmenter.process(question, JiebaSegmenter.SegMode.SEARCH);
                
                // Filter stop words
                String segmentedQuery = tokens.stream()
                        .map(i -> i.word)
                        .filter(word -> !stopWords.contains(word))
                        .collect(Collectors.joining(" "));

                if (segmentedQuery.isBlank()) {
                    segmentedQuery = tokens.stream().map(i -> i.word).collect(Collectors.joining(" "));
                }

                // 构建全文检索的 tsquery：将分词结果去重并转换为 | (或) 逻辑
                String tsQuery = Arrays.stream(segmentedQuery.split("\\s+"))
                        .filter(s -> !s.isBlank())
                        .distinct()
                        .collect(Collectors.joining(" | "));

                if (tsQuery.isBlank()) {
                    tsQuery = question;
                }

                log.info("关键字搜索分词查询：'{}' -> tsquery: '{}'", segmentedQuery, tsQuery);

                String keywordSql = "SELECT id, document_id, content, NULL as content_vector, chunk_index, source_meta, chunker_name, content_keywords, created_at " +
                                    "FROM chunks " +
                                    "WHERE document_id IN (" + documentIdsClause + ") " +
                                    "AND content_search @@ to_tsquery('simple', ?) " +
                                    "ORDER BY ts_rank(content_search, to_tsquery('simple', ?)) DESC " +
                                    "LIMIT ?";
                
                List<Chunk> results = jdbcClient.sql(keywordSql)
                        .params(tsQuery, tsQuery, searchK)
                        .query(new ChunkRowMapper())
                        .list();
                log.info("关键字搜索在 {} 毫秒内找到了 {} 个结果", System.currentTimeMillis() - keywordSearchStart, results.size());
                
                langFuseService.createSpan(null, traceId, currentSpanId, "Keyword Search",
                        Map.of("tsQuery", tsQuery),
                        results.stream().limit(10).collect(Collectors.toMap(
                                Chunk::getId,
                                c -> StringUtils.left(c.getContent(), 20)
                        )), kStart, Instant.now());
                
                return results;
            }, searchExecutor);

            // 3. Wait for results
            CompletableFuture.allOf(vectorSearchFuture, keywordSearchFuture).join();
            List<Chunk> vectorResults = vectorSearchFuture.get();
            List<Chunk> keywordResults = keywordSearchFuture.get();

            // 4. 选择融合策略：重排序 或 RRF
            List<Chunk> finalResults;
            if (rerankEnabled && scoringModel != null) {
                // 合并并去重
                Map<UUID, Chunk> combinedMap = new LinkedHashMap<>();
                vectorResults.forEach(c -> combinedMap.put(c.getId(), c));
                keywordResults.forEach(c -> combinedMap.put(c.getId(), c));
                List<Chunk> candidates = new ArrayList<>(combinedMap.values());
                
                log.info("重排序模式：合并后共有 {} 个候选片段", candidates.size());
                
                // 使用 LangChain4j 标准接口进行评分
                List<TextSegment> segments = candidates.stream()
                        .map(c -> TextSegment.from(c.getContent()))
                        .collect(Collectors.toList());
                
                Instant rerankStartTime = Instant.now();
                Response<List<Double>> scoresResponse = scoringModel.scoreAll(segments, question);
                List<Double> scores = scoresResponse.content();

                // 将分数关联回 Chunk 并排序
                finalResults = new ArrayList<>();
                for (int i = 0; i < candidates.size(); i++) {
                    Chunk candidate = candidates.get(i);
                    double score = i < scores.size() ? scores.get(i) : 0.0;
                    candidate.setScore(score);
                    finalResults.add(candidate);
                }

                finalResults.sort(Comparator.comparingDouble(Chunk::getScore).reversed());
                finalResults = finalResults.stream().limit(topK).collect(Collectors.toList());

                langFuseService.createSpan(null, traceId, currentSpanId, "Reranking",
                        candidates.stream().limit(10).collect(Collectors.toMap(
                                Chunk::getId,
                                c -> StringUtils.left(c.getContent(), 20)
                        )),
                        finalResults.stream().limit(10).collect(Collectors.toMap(
                                Chunk::getId,
                                c -> StringUtils.left(c.getContent(), 20)
                        )),
                        rerankStartTime, Instant.now());
                log.info("重排序完成。最终返回 {} 个片段。", finalResults.size());
            } else {
                // RRF 融合
                int rrfK = 60;    // RRF constant
                Map<UUID, Double> rrfScores = new HashMap<>();
                Map<UUID, Chunk> chunkMap = new HashMap<>();

                for (int i = 0; i < vectorResults.size(); i++) {
                    Chunk chunk = vectorResults.get(i);
                    chunkMap.putIfAbsent(chunk.getId(), chunk);
                    rrfScores.merge(chunk.getId(), 1.0 / (rrfK + i + 1), Double::sum);
                }

                for (int i = 0; i < keywordResults.size(); i++) {
                    Chunk chunk = keywordResults.get(i);
                    chunkMap.putIfAbsent(chunk.getId(), chunk);
                    rrfScores.merge(chunk.getId(), 1.0 / (rrfK + i + 1), Double::sum);
                }

                finalResults = rrfScores.entrySet().stream()
                        .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                        .limit(topK)
                        .map(entry -> chunkMap.get(entry.getKey()))
                        .collect(Collectors.toList());

                log.info("RRF 融合完成。最终得到 {} 个片段。", finalResults.size());
            }

            // 弹出当前 Span 并上报总的 Hybrid Search Span
            TraceContext.popSpan();
            langFuseService.createSpan(currentSpanId, traceId, parentSpanId, "Hybrid Search", question,
                    finalResults.stream().limit(10).collect(Collectors.toMap(
                            Chunk::getId,
                            c -> StringUtils.left(c.getContent(), 20)
                    )), startTime, Instant.now());
            
            return finalResults;

        } catch (Exception e) {
            TraceContext.popSpan();
            log.error("执行混合搜索时出错", e);
            throw new RuntimeException("混合搜索失败", e);
        }
    }

    @Override
    public List<Chunk> batchHybridSearch(List<String> questions, List<UUID> documentIds) {
        if (questions == null || questions.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("执行批量混合搜索，包含 {} 个子查询", questions.size());

        // 使用 stream 顺序执行每个查询的 hybridSearch，确保 TraceContext 正确传递
        List<Chunk> allChunks = questions.stream()
                .map(q -> hybridSearch(q, documentIds))
                .flatMap(List::stream)
                .toList();

        // 简单去重：保留首次出现的片段
        Set<UUID> seenIds = new HashSet<>();
        List<Chunk> distinctChunks = new ArrayList<>();

        for (Chunk c : allChunks) {
            if (seenIds.add(c.getId())) {
                distinctChunks.add(c);
            }
        }

        log.info("批量搜索完成。总片段: {}, 去重后: {}", allChunks.size(), distinctChunks.size());
        return distinctChunks;
    }

    /**
     * RowMapper to map ResultSet to Chunk object
     */
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