package com.twocold.jrag.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "向会话添加文档请求")
public record AddDocumentsRequest(
    @Schema(description = "要关联的文档 ID 列表")
    List<UUID> documentIds
) {}