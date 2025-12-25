package com.example.qarag.service;

import com.example.qarag.config.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * 通用的评分模型实现，支持标准 OpenAI 风格和阿里云百炼风格的重排 API。
 * 无需引入特定厂商的 SDK 依赖。
 */
@Slf4j
public class GenericScoringModel implements ScoringModel {

    private final RagProperties.Retrieval.Rerank config;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GenericScoringModel(RagProperties.Retrieval.Rerank config) {
        this.config = config;
        
        // 设置 30 秒超时
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
                
        this.objectMapper = new ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        if (segments == null || segments.isEmpty()) {
            return Response.from(Collections.emptyList());
        }

        try {
            String fullUrl = config.baseUrl();
            boolean isAliCloud = fullUrl.contains("dashscope.aliyuncs.com");

            // 非阿里且未写 /rerank 则自动补全（针对 TEI/SiliconFlow 等）
            if (!isAliCloud && !fullUrl.endsWith("/rerank")) {
                fullUrl = fullUrl.endsWith("/") ? fullUrl + "rerank" : fullUrl + "/rerank";
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", config.modelName() != null ? config.modelName() : (isAliCloud ? "gte-rerank" : "bge-reranker-v2-m3"));

            if (isAliCloud) {
                // 阿里云百炼格式：{"model": "...", "input": {"query": "...", "documents": [...]}}
                Map<String, Object> input = new HashMap<>();
                input.put("query", query);
                input.put("documents", segments.stream().map(TextSegment::text).toList());
                requestBody.put("input", input);
            } else {
                // 标准格式 (TEI/SiliconFlow)
                requestBody.put("query", query);
                List<String> docs = segments.stream().map(TextSegment::text).toList();
                requestBody.put("documents", docs);
                requestBody.put("texts", docs); // 兼容性：有些 API 使用 texts
            }

            log.info("Sending rerank request to {} for {} documents...", fullUrl, segments.size());
            long start = System.currentTimeMillis();

            String responseBody = restClient.post()
                    .uri(fullUrl)
                    .header("Authorization", (config.apiKey() != null && !config.apiKey().isBlank()) 
                            ? "Bearer " + config.apiKey() : "")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            
            log.info("Rerank API responded in {} ms", System.currentTimeMillis() - start);

            List<Double> scores = parseScores(responseBody, segments.size());
            return Response.from(scores);

        } catch (Exception e) {
            log.error("重排序评分失败: {}", e.getMessage(), e);
            // 失败时返回全 0 分，保持初始排序不崩溃
            return Response.from(segments.stream().map(s -> 0.0).toList());
        }
    }

    private List<Double> parseScores(String responseBody, int expectedSize) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        List<Double> scoreList = new ArrayList<>(Collections.nCopies(expectedSize, 0.0));
        
        JsonNode results = null;
        // 1. 阿里云百炼 {"output": {"results": [...]}}
        if (root.has("output") && root.get("output").has("results")) {
            results = root.get("output").get("results");
        } 
        // 2. 标准包装 {"results": [...]}
        else if (root.has("results")) {
            results = root.get("results");
        } 
        // 3. 直接列表 [...]
        else if (root.isArray()) {
            results = root;
        }

        if (results != null && results.isArray()) {
            for (JsonNode item : results) {
                int index = item.get("index").asInt();
                double score = item.has("relevance_score") 
                        ? item.get("relevance_score").asDouble() 
                        : item.get("score").asDouble();
                if (index >= 0 && index < expectedSize) {
                    scoreList.set(index, score);
                }
            }
        }
        return scoreList;
    }
}
