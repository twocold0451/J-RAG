package com.twocold.jrag.service;

import com.twocold.jrag.config.RagProperties;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
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
        
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        this.objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        this.objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        this.objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        
        if (properties != null && properties.enabled()) {
            this.restClient = RestClient.builder()
                    .baseUrl(properties.baseUrl())
                    .defaultHeader("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString((properties.publicKey() + ":" + properties.secretKey()).getBytes()))
                    .build();
            log.info("LangFuse Ingestion enabled. Base URL: {}", properties.baseUrl());
        } else {
            this.restClient = null;
            log.info("LangFuse observability disabled.");
        }
    }

    private void sendEvent(String type, String eventId, Instant timestamp, Map<String, Object> body) {
        if (restClient == null) return;

        try {
            // LangFuse Ingestion API expects a "batch" array
            Map<String, Object> event = new HashMap<>();
            event.put("id", eventId != null ? eventId : UUID.randomUUID().toString());
            event.put("type", type);
            event.put("timestamp", timestamp.toString());
            event.put("body", body);

            Map<String, Object> payload = new HashMap<>();
            payload.put("batch", List.of(event));

            String jsonBody = objectMapper.writeValueAsString(payload);
            log.debug("Sending Ingestion Event [{}] to LangFuse: {}", type, jsonBody);

            restClient.post()
                    .uri("/api/public/ingestion")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to send {} event to LangFuse: {}", type, e.getMessage());
        }
    }

    @Async
    public void createTrace(String traceId, String name, String userId, Map<String, Object> metadata) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", traceId);
        body.put("name", name);
        body.put("userId", userId);
        body.put("metadata", metadata);
        
        sendEvent("trace-create", UUID.randomUUID().toString(), Instant.now(), body);
    }

    @Async
    public void createGeneration(String id, String traceId, String parentObservationId, String name, 
                                 String model, Object input, Object output, 
                                 Map<String, Object> usage, Instant startTime, Instant endTime) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", id != null ? id : UUID.randomUUID().toString());
        body.put("traceId", traceId);
        if (parentObservationId != null) body.put("parentObservationId", parentObservationId);
        body.put("name", name);
        body.put("model", model);
        body.put("input", input);
        body.put("output", output);
        body.put("startTime", startTime.toString());
        body.put("endTime", endTime.toString());
        
        if (usage != null) {
            usage.putIfAbsent("unit", "TOKENS");
            body.put("usage", usage);
        }

        sendEvent("generation-create", UUID.randomUUID().toString(), Instant.now(), body);
    }

    @Async
    public void createSpan(String id, String traceId, String parentObservationId, String name, 
                           Object input, Object output, 
                           Instant startTime, Instant endTime) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", id != null ? id : UUID.randomUUID().toString());
        body.put("traceId", traceId);
        if (parentObservationId != null) body.put("parentObservationId", parentObservationId);
        body.put("name", name);
        body.put("input", input);
        body.put("output", output);
        body.put("startTime", startTime.toString());
        body.put("endTime", endTime.toString());

        sendEvent("span-create", UUID.randomUUID().toString(), Instant.now(), body);
    }
}
