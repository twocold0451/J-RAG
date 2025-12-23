package com.example.qarag.ingestion;

import java.util.UUID;

import java.nio.file.Path;

public interface IngestionService {
    /**
     * 启动文档的异步解析入库过程。
     *
     * @param documentId 要解析的文档的 UUID。
     * @param tempFilePath 文档的临时文件路径。
     * @param userId 上传文件的用户 ID。
     */
    void startIngestion(UUID documentId, Path tempFilePath, Long userId, boolean isPublic);
}
