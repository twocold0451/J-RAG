package com.twocold.jrag.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twocold.jrag.domain.DocumentStatus;
import com.twocold.jrag.ingestion.chunker.DocumentChunker;
import com.twocold.jrag.ingestion.chunker.DocumentChunkerFactory;
import com.twocold.jrag.service.DocumentService;
import com.twocold.jrag.service.TextAnalysisService;
import com.twocold.jrag.api.dto.DocumentUpdateMessage;
import com.pgvector.PGvector;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionServiceImpl implements IngestionService {

    private final JdbcClient jdbcClient;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final DocumentService documentService;
    private final SimpMessagingTemplate messagingTemplate;
    private final DocumentChunkerFactory chunkerFactory;
    private final ObjectMapper objectMapper;
    private final TextAnalysisService textAnalysisService;

    @Override
    @Async("ingestionTaskExecutor")
    @Transactional
    public void startIngestion(UUID documentId, Path tempFilePath, Long userId, boolean isPublic) {
        try {
            updateIngestionStatus(documentId, userId, DocumentStatus.PROCESSING, 0, null);

            // 1. 获取合适的 Chunker
            String filename = tempFilePath.getFileName().toString();
            DocumentChunker chunker = chunkerFactory.getChunker(filename);

            // 2. 解析阶段 (Parsing) - 进度 0% ~ 60%
            List<TextSegment> rawSegments = parseDocument(documentId, tempFilePath, chunker, userId);
            if (rawSegments == null) return; // 错误已在内部处理

            // 3. 清洗阶段 (Cleaning)
            List<TextSegment> segments = filterSegments(documentId, rawSegments, userId);
            if (segments.isEmpty()) return; // 错误已在内部处理

            // 4. 入库阶段 (Ingestion) - 进度 60% ~ 100%
            storeSegments(documentId, segments, chunker.getClass().getSimpleName(), userId);

            // 5. 完成
            updateIngestionStatus(documentId, userId, DocumentStatus.COMPLETED, 100, null);

        } catch (Exception e) {
            handleIngestionError(documentId, userId, e);
        } finally {
            deleteTempFile(tempFilePath);
        }
    }

    private List<TextSegment> parseDocument(UUID documentId, Path tempFilePath, DocumentChunker chunker, Long userId) {
        final long[] lastUpdateTime = {0};
        
        // 调用 Chunker，映射进度到 0-60%
        List<TextSegment> rawSegments = chunker.chunk(tempFilePath, (processed, total) -> {
            if (total <= 0) return;
            int percent = (int) ((double) processed / total * 60);
            throttleUpdate(documentId, userId, percent, lastUpdateTime, 60);
        });

        if (rawSegments.isEmpty()) {
            String msg = "未提取到内容";
            log.error("文档 {} {}", documentId, msg);
            updateIngestionStatus(documentId, userId, DocumentStatus.FAILED, 0, msg);
            return null;
        }
        return rawSegments;
    }

    private List<TextSegment> filterSegments(UUID documentId, List<TextSegment> rawSegments, Long userId) {
        List<TextSegment> segments = rawSegments.stream()
                .map(this::cleanSegment)
                .filter(seg -> !seg.text().isBlank())
                .toList();

        if (segments.isEmpty()) {
            String msg = "清洗后没有剩余内容";
            log.error("文档 {} {}", documentId, msg);
            updateIngestionStatus(documentId, userId, DocumentStatus.FAILED, 0, msg);
            return Collections.emptyList();
        }
        return segments;
    }

    private void storeSegments(UUID documentId, List<TextSegment> segments, String chunkerName, Long userId) {
        int totalSegments = segments.size();
        int batchSize = 20;
        final long[] lastUpdateTime = {0};

        String insertSql = """
                INSERT INTO chunks(id, document_id, content, content_vector, chunk_index, source_meta, chunker_name, content_keywords, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                """ ;

        for (int i = 0; i < totalSegments; i += batchSize) {
            int end = Math.min(i + batchSize, totalSegments);
            List<TextSegment> batchSegments = segments.subList(i, end);

            // 4.1 Embedding (带重试)
            List<Embedding> batchEmbeddings = embedWithRetry(batchSegments);

            // 4.2 Batch Insert
            final int currentBatchStart = i;
            jdbcTemplate.batchUpdate(insertSql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement ps, int j) throws java.sql.SQLException {
                    TextSegment segment = batchSegments.get(j);
                    float[] embedding = batchEmbeddings.get(j).vector();

                    // 使用 TextAnalysisService 生成关键词
                    String contentKeywords = textAnalysisService.extractKeywords(segment.text());

                    // 序列化元数据
                    String metadataJson = "{}";
                    try {
                        metadataJson = objectMapper.writeValueAsString(segment.metadata().toMap());
                    } catch (Exception e) {
                        log.error("无法为文档 {} 序列化元数据", documentId, e);
                    }

                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, documentId);
                    ps.setString(3, segment.text().replaceAll("\u0000", ""));
                    ps.setObject(4, new PGvector(embedding));
                    ps.setInt(5, currentBatchStart + j);
                    ps.setString(6, metadataJson);
                    ps.setString(7, chunkerName);
                    ps.setString(8, contentKeywords);
                    ps.setObject(9, OffsetDateTime.now());
                }

                @Override
                public int getBatchSize() {
                    return batchSegments.size();
                }
            });

            // 4.3 Update Progress (60-100%)
            int chunksPercent = (int) ((double) end / totalSegments * 40);
            int totalPercent = 60 + chunksPercent;
            
            throttleUpdate(documentId, userId, totalPercent, lastUpdateTime, 100);
            log.info("文档 {} 入库进度: {}/{} (总进度 {}%)", documentId, end, totalSegments, totalPercent);
        }
    }

    private void updateIngestionStatus(UUID documentId, Long userId, DocumentStatus status, int progress, String errorMessage) {
        documentService.updateDocumentStatusAndProgress(documentId, status, progress, errorMessage);
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/document-updates",
                new DocumentUpdateMessage(documentId, status, progress, errorMessage));
    }

    private void throttleUpdate(UUID documentId, Long userId, int percent, long[] lastUpdateTime, int forceThreshold) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastUpdateTime[0];
        boolean shouldUpdate = elapsed > 500 || percent >= forceThreshold;
        if (shouldUpdate) {
            lastUpdateTime[0] = now;
            updateIngestionStatus(documentId, userId, DocumentStatus.PROCESSING, percent, null);
        }
    }

    private void handleIngestionError(UUID documentId, Long userId, Exception e) {
        String errorMessage = e.getMessage();
        if (e.getCause() instanceof java.io.InterruptedIOException
                && e.getCause().getMessage().contains("timeout")) {
            errorMessage = "AI 服务请求超时，请稍后重试。";
        } else if (errorMessage != null && (errorMessage.toLowerCase().contains("timeout")
                || errorMessage.toLowerCase().contains("interruptedioexception"))) {
            errorMessage = "AI 服务请求超时，请稍后重试。";
        } else if (errorMessage == null || errorMessage.trim().isEmpty()) {
            errorMessage = "未知错误，请联系管理员。";
        }

        if (errorMessage.length() > 500) {
            errorMessage = errorMessage.substring(0, 500) + "...";
        }

        log.error("文档 {} 解析入库失败: {}", documentId, e.getMessage(), e);
        updateIngestionStatus(documentId, userId, DocumentStatus.FAILED, 0, errorMessage);
    }

    private void deleteTempFile(Path tempFilePath) {
        try {
            Files.deleteIfExists(tempFilePath);
        } catch (IOException e) {
            log.error("删除临时文件 {} 失败: {}", tempFilePath, e.getMessage(), e);
        }
    }

    /**
     * 带重试机制的 Embedding 调用
     * 针对 API 限流 (Rate Limit) 或临时网络波动进行指数退避重试
     */
    private List<Embedding> embedWithRetry(List<TextSegment> segments) {
        int maxRetries = 3;
        long waitTime = 2000; // 初始等待 2 秒

        for (int i = 0; i < maxRetries; i++) {
            try {
                return embeddingModel.embedAll(segments).content();
            } catch (RuntimeException e) {
                // 检查是否是最后一次尝试
                if (i == maxRetries - 1) {
                    throw e;
                }
                
                // 简单的错误信息检查，适配常见的 API 错误
                String msg = e.getMessage();
                boolean isRateLimit = msg != null && (msg.contains("429") || msg.contains("Too Many Requests") || msg.contains("Quota"));
                boolean isTimeout = msg != null && (msg.contains("timeout") || msg.contains("Time out"));

                if (isRateLimit || isTimeout) {
                    log.warn("Embedding API 调用失败 (尝试 {}/{}) : {}. 等待 {}ms 后重试...", 
                            i + 1, maxRetries, e.getMessage(), waitTime);
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试等待被中断", ie);
                    }
                    waitTime *= 2; // 指数退避
                } else {
                    // 如果不是限流或超时类错误，直接抛出，不重试
                    throw e;
                }
            }
        }
        throw new RuntimeException("Embedding 重试失败");
    }

    private TextSegment cleanSegment(TextSegment segment) {
        String text = segment.text();
        List<Pattern> patternsToRemove = Arrays.asList(
                Pattern.compile("(?i)^\\s*page\\s+\\d+.*$", Pattern.MULTILINE),
                Pattern.compile("(?i)^\\s*confidential\\s*$", Pattern.MULTILINE),
                Pattern.compile("(?i)^\\s*internal use only\\s*$", Pattern.MULTILINE));

        for (Pattern pattern : patternsToRemove) {
            text = pattern.matcher(text).replaceAll("");
        }
        return TextSegment.from(text.trim(), segment.metadata());
    }
}
