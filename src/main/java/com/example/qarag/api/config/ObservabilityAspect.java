package com.example.qarag.api.config;

import com.example.qarag.config.TraceContext;
import com.example.qarag.service.LangFuseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ObservabilityAspect {

    private final LangFuseService langFuseService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(observed)")
    public Object observe(ProceedingJoinPoint joinPoint, Observed observed) throws Throwable {
        String operationName = observed.name();
        if (operationName.isEmpty()) {
            operationName = joinPoint.getSignature().getName();
        }

        log.debug("Starting observation for: {}", operationName);

        // 1. Context Setup
        String traceId = TraceContext.getTraceId();
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            TraceContext.setTraceId(traceId);
            log.debug("Generated new TraceID: {}", traceId);
        } else {
            log.debug("Using existing TraceID: {}", traceId);
        }

        String parentSpanId = TraceContext.getCurrentSpanId();
        String currentSpanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        TraceContext.pushSpan(currentSpanId);
        log.debug("Pushed SpanID: {} (Parent: {})", currentSpanId, parentSpanId);

        // 2. OpenTelemetry Start

        try {
            Instant startTime = Instant.now();
            Object input = null;
            if (observed.captureInput()) {
                try {
                    // Pass joinPoint to processInput to access parameter names
                    input = processInput(joinPoint, observed);
                } catch (Exception e) {
                    log.warn("Failed to capture input for observation", e);
                }
            }

            Object result;
            try {
                // 3. Execute Business Logic
                result = joinPoint.proceed();
                
                // 4. Success Handling
                if (observed.captureOutput() && result != null) {
                    try {
                        // Apply Filtering and Limiting
                        Object processedOutput = processOutput(result, observed);

                        langFuseService.createSpan(currentSpanId, traceId, parentSpanId, operationName, 
                                input, processedOutput, startTime, Instant.now());
                        log.debug("Recorded success span for: {}", operationName);
                    } catch (Exception e) {
                        log.warn("Failed to capture output for observation", e);
                        langFuseService.createSpan(currentSpanId, traceId, parentSpanId, operationName, 
                                input, "Output processing failed", startTime, Instant.now());
                    }
                } else {
                     langFuseService.createSpan(currentSpanId, traceId, parentSpanId, operationName, 
                                    input, null, startTime, Instant.now());
                     log.debug("Recorded success span (no output) for: {}", operationName);
                }

                return result;

            } catch (Throwable t) {
                log.error("Exception in observed method: {}", operationName, t);
                langFuseService.createSpan(currentSpanId, traceId, parentSpanId, operationName,
                        input, "Error: " + t.getMessage(), startTime, Instant.now());
                throw t;
            }
        } finally {
            String popped = TraceContext.popSpan();
            log.debug("Popped SpanID: {}", popped);
        }
    }

    private Object processInput(ProceedingJoinPoint joinPoint, Observed observed) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) return null;

        // 1. Get parameter names
        String[] paramNames = null;
        try {
            paramNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
        } catch (Exception e) {
            log.debug("Failed to retrieve parameter names", e);
        }
        
        // 2. Construct a Map of ParamName -> Value
        // If parameter names are not available (e.g. compiled without -parameters), fallback to arg0, arg1...
        java.util.Map<String, Object> inputMap = new java.util.LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String name = (paramNames != null && i < paramNames.length) ? paramNames[i] : "arg" + i;
            inputMap.put(name, args[i]);
        }

        // 3. Filter if needed
        if (observed.includeInputFields().length > 0) {
            java.util.Map<String, Object> filtered = new java.util.LinkedHashMap<>();
            for (String field : observed.includeInputFields()) {
                if (inputMap.containsKey(field)) {
                    filtered.put(field, inputMap.get(field));
                }
            }
            return filtered;
        }
        
        // Return full map
        return inputMap;
    }

    private Object processOutput(Object result, Observed observed) {
        if (result == null) return null;

        // 1. Handle Collection Limit
        Object target = result;
        if (observed.collectionLimit() >= 0 && result instanceof java.util.Collection<?> col) {
            if (col.size() > observed.collectionLimit()) {
                target = col.stream().limit(observed.collectionLimit()).toList();
            }
        }

        // 2. Handle Field Filtering
        if (observed.includeOutFields().length > 0) {
            return filterFields(target, observed.includeOutFields());
        }

        return target;
    }

    private Object filterFields(Object target, String[] fields) {
        if (target == null) return null;

        // If it's a collection, filter each element
        if (target instanceof java.util.Collection) {
            return ((java.util.Collection<?>) target).stream()
                    .map(item -> filterSingleObject(item, fields))
                    .toList();
        }
        
        return filterSingleObject(target, fields);
    }

    private Object filterSingleObject(Object obj, String[] fields) {
        try {
            // Convert POJO to Map
            java.util.Map<String, Object> map = objectMapper.convertValue(obj, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
            // Filter Map
            java.util.Map<String, Object> filtered = new java.util.HashMap<>();
            for (String field : fields) {
                if (map.containsKey(field)) {
                    filtered.put(field, map.get(field));
                }
            }
            return filtered;
        } catch (Exception e) {
            // Fallback for simple types (String, Integer) that cannot be converted to Map
            // or if conversion fails
            return obj;
        }
    }

}
