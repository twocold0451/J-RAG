package com.twocold.jrag.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "URL 摄取请求")
public record UrlIngestRequest(
        @Schema(description = "要抓取的网页 URL", example = "https://example.com") String url,
        @Schema(description = "是否设为公开文档") boolean isPublic
) {
}