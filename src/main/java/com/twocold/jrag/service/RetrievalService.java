package com.twocold.jrag.service;

import com.twocold.jrag.config.ObservationUtil;
import com.twocold.jrag.config.Observed;
import com.twocold.jrag.config.RagProperties;
import com.twocold.jrag.config.TraceContext;
import com.twocold.jrag.domain.Chunk;
import com.twocold.jrag.utils.MmrUtils;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

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
    private final ObservationUtil observationUtil;

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
                        com.twocold.jrag.utils.LogMaskingUtils.maskQuery(question), 
                        (rerankEnabled ? "重排序" : "RRF 融合"), documentIds.size(), searchK);
            }

            if (documentIds.isEmpty()) {
                log.info("混合搜索未提供文档 ID，返回空列表。");
                return Collections.emptyList();
            }

            String documentIdsClause = documentIds.stream()
                    .map(uuid -> "'" + uuid.toString() + "'")
                    .collect(Collectors.joining(", "));

            // 1. 向量搜索
            Mono<List<Chunk>> vectorSearchMono = executeVectorSearch(
                    question, documentIdsClause, searchK, parentSpanId, traceId);

            // 2. 关键词搜索
            Mono<List<Chunk>> keywordSearchMono = executeKeywordSearch(
                    question, documentIdsClause, searchK, parentSpanId, traceId);

            // 使用Mono.zip并行执行两个搜索
            return Mono.zip(vectorSearchMono, keywordSearchMono)
                    .flatMap(tuple -> {
                        List<Chunk> vectorResults = tuple.getT1();
                        List<Chunk> keywordResults = tuple.getT2();
                        return processSearchResults(vectorResults, keywordResults,
                                question, topK, rerankEnabled, traceId, parentSpanId);
                    })
                    .block(); // 保持同步返回（暂时）

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

    /**
     * 向量搜索（响应式）- 带详细子步骤观察
     * @param question 查询问题
     * @param documentIdsClause 文档ID列表SQL片段
     * @param searchK 搜索数量
     * @param parentSpanId 父span ID
     * @param traceId trace ID
     * @return Mono<List<Chunk>>
     */
    Mono<List<Chunk>> executeVectorSearch(String question, String documentIdsClause, int searchK,
                                          String parentSpanId, String traceId) {
        // 当前向量搜索的span ID
        String currentSpanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Instant vectorSearchStart = Instant.now();

        return Mono.fromCallable(() -> {
            // 步骤1：生成嵌入向量
            float[] queryEmbedding = observationUtil.observeStep(
                    "Embedding Generation",
                    traceId,
                    currentSpanId,
                    Map.of("query", question, "queryLength", question.length()),
                    () -> {
                        TextSegment questionSegment = TextSegment.from(question);
                        return embeddingModel.embedAll(List.of(questionSegment))
                                .content().getFirst().vector();
                    }
            );

            // 步骤2：向量数据库查询
            List<Chunk> initialResults = observationUtil.observeStep(
                    "Vector Database Query",
                    traceId,
                    currentSpanId,
                    Map.of("searchK", searchK, "fetchK", searchK * 3),
                    () -> {
                        String vectorSql = "SELECT id, document_id, content, content_vector, chunk_index, source_meta, " +
                                "chunker_name, content_keywords, created_at " +
                                "FROM chunks WHERE document_id IN (" + documentIdsClause + ") " +
                                "ORDER BY content_vector <=> ?::vector LIMIT ?";
                        return jdbcClient.sql(vectorSql)
                                .params(new PGvector(queryEmbedding), searchK * 3)
                                .query(new ChunkRowMapper())
                                .list();
                    }
            );

            // 步骤3：MMR去重
            List<Chunk> finalResults = observationUtil.observeStep(
                    "MMR Deduplication",
                    traceId,
                    currentSpanId,
                    Map.of("candidates", initialResults.size(), "targetK", searchK),
                    () -> {
                        List<Chunk> results = MmrUtils.applyMmr(initialResults, queryEmbedding, searchK, 0.5);
                        results.forEach(c -> c.setContentVector(null));
                        return results;
                    }
            );

            // 记录整个向量搜索的总时间
            langFuseService.createSpan(
                    currentSpanId,
                    traceId,
                    parentSpanId,
                    "Vector Search",
                    Map.of("query", com.twocold.jrag.utils.LogMaskingUtils.maskQuery(question)),
                    finalResults.stream().limit(10).collect(Collectors.toMap(
                            chunk -> chunk.getId().toString(),
                            c -> StringUtils.left(c.getContent(), 20)
                    )),
                    vectorSearchStart,
                    Instant.now()
            );
            return finalResults;
        }).subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(results -> log.debug("向量搜索完成: {} 个结果", results.size()))
                .doOnError(e -> log.error("向量搜索失败", e));
    }

    /**
     * 关键词搜索（响应式）- 带详细子步骤观察
     * @param question 查询问题
     * @param documentIdsClause 文档ID列表SQL片段
     * @param searchK 搜索数量
     * @param parentSpanId 父span ID
     * @param traceId trace ID
     * @return Mono<List<Chunk>>
     */
    Mono<List<Chunk>> executeKeywordSearch(String question, String documentIdsClause, int searchK,
                                            String parentSpanId, String traceId) {
        // 当前关键词搜索的span ID
        String currentSpanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Instant keywordSearchStart = Instant.now();

        return Mono.fromCallable(() -> {
            // 步骤1：中文分词
            List<SegToken> tokens = observationUtil.observeStep(
                    "Chinese Tokenization",
                    traceId,
                    currentSpanId,
                    Map.of("query", question, "queryLength", question.length()),
                    () -> jiebaSegmenter.process(question, JiebaSegmenter.SegMode.SEARCH)
            );

            // 步骤2：构建查询（停用词过滤 + tsquery 构建）
            String tsQuery = observationUtil.observeStep(
                    "Build TSQuery",
                    traceId,
                    currentSpanId,
                    Map.of("tokenCount", tokens.size()),
                    () -> {
                        String segmentedQuery = tokens.stream()
                                .map(token -> token.word)
                                .filter(word -> !stopWords.contains(word))
                                .collect(Collectors.joining(" "));

                        if (segmentedQuery.isBlank()) {
                            segmentedQuery = tokens.stream()
                                    .map(token -> token.word)
                                    .collect(Collectors.joining(" "));
                        }

                        String query = Arrays.stream(segmentedQuery.split("\\s+"))
                                .filter(s -> !s.isBlank())
                                .distinct()
                                .collect(Collectors.joining(" | "));

                        return query.isBlank() ? question : query;
                    }
            );

            log.debug("关键词搜索分词：'{}' -> tsquery: '{}'",
                    com.twocold.jrag.utils.LogMaskingUtils.maskQuery(question), tsQuery);

            // 步骤3：关键词数据库查询
            List<Chunk> results = observationUtil.observeStep(
                    "Keyword Database Query",
                    traceId,
                    currentSpanId,
                    Map.of("tsQuery", tsQuery, "searchK", searchK),
                    () -> {
                        String keywordSql = "SELECT id, document_id, content, NULL as content_vector, " +
                                "chunk_index, source_meta, chunker_name, content_keywords, created_at " +
                                "FROM chunks WHERE document_id IN (" + documentIdsClause + ") " +
                                "AND content_search @@ to_tsquery('simple', ?) " +
                                "ORDER BY ts_rank(content_search, to_tsquery('simple', ?)) DESC LIMIT ?";

                        return jdbcClient.sql(keywordSql)
                                .params(tsQuery, tsQuery, searchK)
                                .query(new ChunkRowMapper())
                                .list();
                    }
            );

            // 记录整个关键词搜索的总时间
            langFuseService.createSpan(
                    currentSpanId,
                    traceId,
                    parentSpanId,
                    "Keyword Search",
                    Map.of("query", com.twocold.jrag.utils.LogMaskingUtils.maskQuery(question)),
                    results.stream().limit(10).collect(Collectors.toMap(
                            chunk -> chunk.getId().toString(),
                            c -> StringUtils.left(c.getContent(), 20)
                    )),
                    keywordSearchStart,
                    Instant.now()
            );

            return results;

        }).subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(results -> log.debug("关键词搜索完成: {} 个结果", results.size()))
                .doOnError(e -> log.error("关键词搜索失败", e));
    }

    /**
     * 处理搜索结果（RRF融合或重排序）
     */
    private Mono<List<Chunk>> processSearchResults(List<Chunk> vectorResults, List<Chunk> keywordResults,
                                                   String question, int topK, boolean rerankEnabled,
                                                   String traceId, String parentSpanId) {
        return Mono.fromCallable(() -> {
            if (rerankEnabled && scoringModel != null) {
                return rerank(vectorResults, keywordResults, question, topK, traceId, parentSpanId);
            } else {
                return rrfFusion(vectorResults, keywordResults, topK);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * RRF融合
     */
    private List<Chunk> rrfFusion(List<Chunk> vectorResults, List<Chunk> keywordResults, int topK) {
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

        List<Chunk> finalResults = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> chunkMap.get(entry.getKey()))
                .collect(Collectors.toList());

        log.debug("RRF融合完成。最终得到 {} 个片段。", finalResults.size());
        return finalResults;
    }

    /**
     * 重排序
     */
    private List<Chunk> rerank(List<Chunk> vectorResults, List<Chunk> keywordResults,
                              String question, int topK, String traceId, String parentSpanId) {

        Map<UUID, Chunk> combinedMap = new LinkedHashMap<>();
        vectorResults.forEach(c -> combinedMap.put(c.getId(), c));
        keywordResults.forEach(c -> combinedMap.put(c.getId(), c));
        List<Chunk> candidates = new ArrayList<>(combinedMap.values());

        log.debug("重排序模式：合并后共有 {} 个候选片段", candidates.size());

        // 使用ObservationUtil记录重排序
        List<Chunk> finalResults = observationUtil.observeStep(
            "Reranking",
            traceId,
            parentSpanId,
            Map.of("candidates", candidates.size()),
            () -> {
                List<TextSegment> segments = candidates.stream()
                        .map(c -> TextSegment.from(c.getContent()))
                        .collect(Collectors.toList());

                Response<List<Double>> scoresResponse = scoringModel.scoreAll(segments, question);
                List<Double> scores = scoresResponse.content();

                List<Chunk> results = new ArrayList<>();
                for (int i = 0; i < candidates.size(); i++) {
                    Chunk candidate = candidates.get(i);
                    double score = i < scores.size() ? scores.get(i) : 0.0;
                    candidate.setScore(score);
                    results.add(candidate);
                }

                results.sort(Comparator.comparingDouble(Chunk::getScore).reversed());
                return results.stream().limit(topK).collect(Collectors.toList());
            }
        );

        log.debug("重排序完成。最终返回 {} 个片段。", finalResults.size());
        return finalResults;
    }
}
