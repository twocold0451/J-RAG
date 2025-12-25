package com.example.qarag.service;

import com.example.qarag.config.RagProperties;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class LangFuseService {

    private final RestClient restClient;
    private final RagProperties.LangFuse properties;
    private final ObjectMapper objectMapper;

    public LangFuseService(RagProperties ragProperties) {
        this.properties = ragProperties.langfuse();
        
        // Create a dedicated ObjectMapper for observability data
        this.objectMapper = new ObjectMapper();
        // 1. Don't fail if a class has no public getters (like many AI message classes)
        this.objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        // 2. Force Jackson to look at private fields directly
        this.objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        this.objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        
        if (properties != null && properties.enabled()) {
            this.restClient = RestClient.builder()
                    .baseUrl(properties.baseUrl())
                    .defaultHeader("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString((properties.publicKey() + ":" + properties.secretKey()).getBytes()))
                    .build();
            log.info("LangFuse observability enabled. Base URL: {}", properties.baseUrl());
        } else {
            this.restClient = null;
            log.info("LangFuse observability disabled.");
        }
    }

    @Async
    public void createTrace(String traceId, String name, String userId, Map<String, Object> metadata) {
        if (restClient == null) return;
        
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("id", traceId);
            body.put("name", name);
            body.put("userId", userId);
            body.put("metadata", metadata);
            body.put("timestamp", Instant.now().toString());

            String jsonBody = objectMapper.writeValueAsString(body);
            log.debug("Sending Trace to LangFuse: {}", jsonBody);

            restClient.post()
                    .uri("/api/public/traces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to send trace to LangFuse: {}", e.getMessage());
        }
    }

    @Async
    public void createGeneration(String id, String traceId, String parentObservationId, String name, 
                                 String model, Object input, Object output, 
                                 Map<String, Object> usage, Instant startTime, Instant endTime) {
        if (restClient == null) return;

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("id", id != null ? id : UUID.randomUUID().toString());
            body.put("traceId", traceId);
            if (parentObservationId != null) body.put("parentObservationId", parentObservationId);
            body.put("name", name);
            body.put("model", model);
            body.put("input", input);
            body.put("output", output);
            body.put("usage", usage);
            body.put("startTime", startTime.toString());
            body.put("endTime", endTime.toString());
            body.put("type", "GENERATION");

            String jsonBody = objectMapper.writeValueAsString(body);
            log.debug("Sending Trace to LangFuse: {}", jsonBody);

            restClient.post()
                    .uri("/api/public/generations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to send generation to LangFuse: {}", e.getMessage());
        }
    }

    @Async
    public void createSpan(String id, String traceId, String parentObservationId, String name, 
                           Object input, Object output, 
                           Instant startTime, Instant endTime) {
        if (restClient == null) return;

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("id", id != null ? id : UUID.randomUUID().toString());
            body.put("traceId", traceId);
            if (parentObservationId != null) body.put("parentObservationId", parentObservationId);
            body.put("name", name);
            body.put("input", input);
            body.put("output", output);
            body.put("startTime", startTime.toString());
            body.put("endTime", endTime.toString());
            body.put("type", "SPAN");

            String jsonBody = objectMapper.writeValueAsString(body);
            log.debug("Sending Trace to LangFuse: {}", jsonBody);

            restClient.post()
                    .uri("/api/public/spans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to send span to LangFuse: {}", e.getMessage());
        }
    }
}