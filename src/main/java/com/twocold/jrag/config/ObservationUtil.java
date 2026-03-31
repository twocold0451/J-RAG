package com.twocold.jrag.config;

import com.twocold.jrag.service.LangFuseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 观察工具类
 * 用于手动记录子步骤的执行时间和输入输出
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ObservationUtil {

    private final LangFuseService langFuseService;

    /**
     * 记录一个子步骤，自动处理成功和失败
     *
     * @param name        步骤名称
     * @param traceId     trace ID
     * @param parentSpanId 父 span ID
     * @param input       输入参数
     * @param action      要执行的操作
     * @return 操作结果
     */
    public <T> T observeStep(String name,
                             String traceId,
                             String parentSpanId,
                             Map<String, Object> input,
                             Supplier<T> action) {
        String spanId = generateSpanId();
        Instant start = Instant.now();

        try {
            T result = action.get();

            langFuseService.createSpan(
                    spanId,
                    traceId,
                    parentSpanId,
                    name,
                    input,
                    summarizeOutput(result),
                    start,
                    Instant.now()
            );

            return result;

        } catch (Exception e) {
            log.error("Step '{}' failed", name, e);
            langFuseService.createSpan(
                    spanId,
                    traceId,
                    parentSpanId,
                    name,
                    input,
                    Map.of("error", e.getMessage()),
                    start,
                    Instant.now()
            );
            throw e;
        }
    }

    /**
     * 简化版：只记录时间和名称，无返回值
     */
    public void observeSimple(String name,
                              String traceId,
                              String parentSpanId,
                              Runnable action) {
        observeStep(name, traceId, parentSpanId, null, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 生成 span ID
     */
    private String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 简化输出结果，只保留基本类型，避免序列化复杂对象
     */
    private Object summarizeOutput(Object result) {
        if (result == null) {
            return null;
        }
        // 只处理基本类型和简单集合
        if (result instanceof String) {
            return result;
        }
        if (result instanceof Number) {
            return result;
        }
        if (result instanceof Boolean) {
            return result;
        }
        if (result instanceof List) {
            return Map.of("size", ((List<?>) result).size());
        }
        if (result instanceof float[]) {
            return Map.of("length", ((float[]) result).length);
        }
        if (result instanceof double[]) {
            return Map.of("length", ((double[]) result).length);
        }
        if (result instanceof int[]) {
            return Map.of("length", ((int[]) result).length);
        }
        if (result instanceof long[]) {
            return Map.of("length", ((long[]) result).length);
        }
        if (result.getClass().isArray()) {
            return Map.of("length", ((Object[]) result).length);
        }
        // 对于其他复杂对象，只返回类名
        return Map.of("type", result.getClass().getSimpleName());
    }
}
