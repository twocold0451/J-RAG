package com.example.qarag.ingestion;

import java.util.UUID;

import java.nio.file.Path;

public interface IngestionService {
    /**
     * Starts the asynchronous ingestion process for a document.
     *
     * @param documentId The UUID of the document to ingest.
     * @param tempFilePath The temporary file path of the document.
     * @param userId The ID of the user uploading the file.
     */
    void startIngestion(UUID documentId, Path tempFilePath, Long userId, boolean isPublic);
}
