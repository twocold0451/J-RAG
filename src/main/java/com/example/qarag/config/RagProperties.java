package com.example.qarag.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.rag")
@Validated
public record RagProperties(
                Retrieval retrieval,
                Chunking chunking,
                Vision vision) {

        public record Retrieval(
                        @Min(1) @Max(20) int topK) {
        }

        public record Chunking(
                        @Min(100) int size,
                        @Min(0) int overlap) {
        }

        /**
         * 视觉模型配置 (用于处理 PDF 中的图片、图表、扫描件)
         */
        public record Vision(
                        /** 是否启用视觉模型处理 */
                        boolean enabled,
                        /** API 基础 URL */
                        String baseUrl,
                        /** API Key */
                        String apiKey,
                        /** 模型名称 (如 gpt-4-vision-preview, moonshot-v1-vision) */
                        String modelName,
                        /** 请求超时时间 (秒) */
                        int timeoutSeconds) {
                public Vision {
                        if (timeoutSeconds <= 0) {
                                timeoutSeconds = 60;
                        }
                }
        }
}
