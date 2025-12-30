package com.example.qarag.service;

import com.example.qarag.api.config.Observed;
import com.example.qarag.config.RagProperties;
import com.example.qarag.config.TraceContext;
import com.example.qarag.domain.Chunk;
import com.example.qarag.utils.MmrUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import com.pgvector.PGvector;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final EmbeddingModel embeddingModel;
    private final RagProperties ragProperties;
    private final JdbcClient jdbcClient;
    private final Executor searchExecutor;
    private final ScoringModel scoringModel;
    private final LangFuseService langFuseService;
    private final ApplicationContext applicationContext;

    static {
        new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

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
                log.error("资源中未找到 stopwords.txt。");
            }
        } catch (Exception e) {
            log.error("加载停用词失败", e);
        }
    }

    /**
     * 执行混合搜索（向量 + 关键字）以检索相关片段，可选择按文档 ID 进行过滤。
     *
     * @param question 用户的问题。
     * @param documentIds 用于过滤搜索的可选文档 ID 列表。
     * @return 包含前 K 个相关片段的列表。
     */
    @Observed(name = "Hybrid Search", includeOutFields = {"id", "content", "score", "documentId"}, collectionLimit = 10)
    public List<Chunk> hybridSearch(String question, List<UUID> documentIds) {
        String traceId = TraceContext.getTraceId();
        String parentSpanId = TraceContext.getCurrentSpanId();
        try {
            boolean rerankEnabled = ragProperties.retrieval().rerank() != null && ragProperties.retrieval().rerank().enabled();
            int topK = ragProperties.retrieval().topK();

            int searchK = rerankEnabled ? ragProperties.retrieval().rerank().initialTopK() : topK;

            if (log.isDebugEnabled()) {
                log.debug("开始混合搜索问题：'{}'，模式：{}，涉及 {} 个文档，searchK：{}",
                        com.example.qarag.utils.LogMaskingUtils.maskQuery(question), 
                        (rerankEnabled ? "重排序" : "RRF 融合"), documentIds.size(), searchK);
            }

            if (documentIds.isEmpty()) {
                log.info("混合搜索未提供文档 ID，返回空列表。");
                return Collections.emptyList();
            }

            String documentIdsClause = documentIds.stream()
                    .map(uuid -> "'" + uuid.toString() + "'")
                    .collect(Collectors.joining(", "));

            // 1. Prepare Vector Search Task
            CompletableFuture<List<Chunk>> vectorSearchFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    Instant startTime = Instant.now();
                    long vectorSearchStart = System.currentTimeMillis();
                    TextSegment questionSegment = TextSegment.from(question);
                    float[] queryEmbedding = embeddingModel.embedAll(List.of(questionSegment)).content().getFirst().vector();
                    String vectorSql = "SELECT id, document_id, content, content_vector, chunk_index, source_meta, chunker_name, content_keywords, created_at " +
                            "FROM chunks " +
                            "WHERE document_id IN (" + documentIdsClause + ") " +
                            "ORDER BY content_vector <=> ? " +
                            "LIMIT ?";
                    // MMR Parameters
                    int fetchK = searchK * 3; 
                    double mmrLambda = 0.5;   
                    List<Chunk> initialResults = jdbcClient.sql(vectorSql)
                            .params(new PGvector(queryEmbedding), fetchK)
                            .query(new ChunkRowMapper())
                            .list();
                    log.debug("向量搜索在 {} 毫秒内获取了 {} 个候选片段", System.currentTimeMillis() - vectorSearchStart, initialResults.size());

                    List<Chunk> finalResults = MmrUtils.applyMmr(initialResults, queryEmbedding, searchK, mmrLambda);
                    finalResults.forEach(c -> c.setContentVector(null));

                    langFuseService.createSpan(null, traceId, parentSpanId, "Vector Search", null,
                            finalResults.stream().limit(10).collect(Collectors.toMap(
                                    chunk -> chunk.getId().toString(),
                                    c -> StringUtils.left(c.getContent(), 20)
                            )),
                            startTime, Instant.now());

                    return finalResults;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, searchExecutor);

            // 2. Prepare Keyword Search Task
            CompletableFuture<List<Chunk>> keywordSearchFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    long keywordSearchStart = System.currentTimeMillis();
                    Instant startTime = Instant.now();
                    List<SegToken> tokens = jiebaSegmenter.process(question, JiebaSegmenter.SegMode.SEARCH);

                    String segmentedQuery = tokens.stream()
                            .map(i -> i.word)
                            .filter(word -> !stopWords.contains(word))
                            .collect(Collectors.joining(" "));
                    if (segmentedQuery.isBlank()) {
                        segmentedQuery = tokens.stream().map(i -> i.word).collect(Collectors.joining(" "));
                    }

                    String tsQuery = Arrays.stream(segmentedQuery.split("\\s+"))
                            .filter(s -> !s.isBlank())
                            .distinct()
                            .collect(Collectors.joining(" | "));
                    if (tsQuery.isBlank()) {
                        tsQuery = question;
                    }
                    log.debug("关键字搜索分词查询：'{}' -> tsquery: '{}'", 
                            com.example.qarag.utils.LogMaskingUtils.maskQuery(segmentedQuery), tsQuery);
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
                    log.debug("关键字搜索在 {} 毫秒内找到了 {} 个结果", System.currentTimeMillis() - keywordSearchStart, results.size());

                    langFuseService.createSpan(null, traceId, parentSpanId, "Keyword Search",
                            Map.of("tsQuery", tsQuery),
                            results.stream().limit(10).collect(Collectors.toMap(
                                    chunk -> chunk.getId().toString(),
                                    c -> StringUtils.left(c.getContent(), 20)
                            )),
                            startTime, Instant.now());

                    return results;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, searchExecutor);

            CompletableFuture.allOf(vectorSearchFuture, keywordSearchFuture).join();
            List<Chunk> vectorResults = vectorSearchFuture.get();
            List<Chunk> keywordResults = keywordSearchFuture.get();

            List<Chunk> finalResults;
            if (rerankEnabled && scoringModel != null) {
                Map<UUID, Chunk> combinedMap = new LinkedHashMap<>();
                vectorResults.forEach(c -> combinedMap.put(c.getId(), c));
                keywordResults.forEach(c -> combinedMap.put(c.getId(), c));
                List<Chunk> candidates = new ArrayList<>(combinedMap.values());
                
                log.debug("重排序模式：合并后共有 {} 个候选片段", candidates.size());
                List<TextSegment> segments = candidates.stream()
                        .map(c -> TextSegment.from(c.getContent()))
                        .collect(Collectors.toList());
                Response<List<Double>> scoresResponse = scoringModel.scoreAll(segments, question);
                List<Double> scores = scoresResponse.content();

                finalResults = new ArrayList<>();
                for (int i = 0; i < candidates.size(); i++) {
                    Chunk candidate = candidates.get(i);
                    double score = i < scores.size() ? scores.get(i) : 0.0;
                    candidate.setScore(score);
                    finalResults.add(candidate);
                }

                finalResults.sort(Comparator.comparingDouble(Chunk::getScore).reversed());
                finalResults = finalResults.stream().limit(topK).collect(Collectors.toList());
                
                log.debug("重排序完成。最终返回 {} 个片段。", finalResults.size());
            } else {
                int rrfK = 60;
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

                log.debug("RRF 融合完成。最终得到 {} 个片段。", finalResults.size());
            }

            return finalResults;

        } catch (Exception e) {
            log.error("执行混合搜索时出错", e);
            throw new RuntimeException("混合搜索失败", e);
        }
    }

    /**
     * 批量执行混合搜索。对每个问题并行执行搜索，然后汇总并去重结果。
     *
     * @param questions 问题列表。
     * @param documentIds 用于过滤搜索的可选文档 ID 列表。
     * @return 汇总后的相关片段列表。
     */
    @Observed(name = "Batch Hybrid Search",includeInputFields = {"questions"},includeOutFields = {"id","content"},collectionLimit = 10)
    public List<Chunk> batchHybridSearch(List<String> questions, List<UUID> documentIds) {
        if (questions == null || questions.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("执行批量混合搜索，包含 {} 个子查询", questions.size());

        // 使用 ApplicationContext 获取代理对象以确保 @Observed 切面生效
        RetrievalService self = applicationContext.getBean(RetrievalService.class);

        List<Chunk> allChunks = questions.stream()
                .map(q -> self.hybridSearch(q, documentIds))
                .flatMap(List::stream)
                .toList();

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

    private static class ChunkRowMapper implements RowMapper<Chunk> {
        @Override
        public Chunk mapRow(ResultSet rs, int rowNum) throws SQLException {
            Chunk chunk = new Chunk();
            chunk.setId(UUID.fromString(rs.getString("id")));
            chunk.setDocumentId(UUID.fromString(rs.getString("document_id")));
            chunk.setContent(rs.getString("content"));
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
