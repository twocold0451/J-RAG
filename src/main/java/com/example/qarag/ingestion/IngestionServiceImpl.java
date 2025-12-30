package com.example.qarag.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.qarag.domain.DocumentStatus;
import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import com.example.qarag.ingestion.chunker.DocumentChunker;
import com.example.qarag.ingestion.chunker.DocumentChunkerFactory;
import com.example.qarag.service.DocumentService;
import com.example.qarag.api.dto.DocumentUpdateMessage;
import com.pgvector.PGvector;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionServiceImpl implements IngestionService {

    private final JdbcClient jdbcClient;
    private final EmbeddingModel embeddingModel;
    private final DocumentService documentService;
    private final SimpMessagingTemplate messagingTemplate;
    private final DocumentChunkerFactory chunkerFactory;
    private final ObjectMapper objectMapper;
    private final JiebaSegmenter jiebaSegmenter = new JiebaSegmenter();
    private final Set<String> stopWords = new HashSet<>();

    @PostConstruct
    public void loadStopWords() {
        try {
            ClassPathResource resource = new ClassPathResource("stopwords.txt");
            if (resource.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.trim().isEmpty()) {
                            stopWords.add(line.trim());
                        }
                    }
                }
                log.info("IngestionService: 已加载 {} 个停用词。", stopWords.size());
            } else {
                log.error("IngestionService: 未找到 stopwords.txt。");
            }
        } catch (Exception e) {
            log.error("IngestionService: Failed to load stop words", e);
        }
    }

    @Override
    @Async
    @Transactional
    public void startIngestion(UUID documentId, Path tempFilePath, Long userId, boolean isPublic) {
        try {
            documentService.updateDocumentStatusAndProgress(documentId, DocumentStatus.PROCESSING, 0, null);

            // 1. 使用工厂获取合适的 Chunker
            String filename = tempFilePath.getFileName().toString();
            DocumentChunker chunker = chunkerFactory.getChunker(filename);
            
            // 2. 切分文档 (解析责任下放给 Chunker)
            // Chunker 自行决定如何读取文件 (例如 PDF 需要精细读取，Excel 需要 POI)
            List<TextSegment> rawSegments = chunker.chunk(tempFilePath);

            if (rawSegments.isEmpty()) {
                log.error("文档 {} 未找到任何文本片段", documentId);
                documentService.updateDocumentStatusAndProgress(documentId, DocumentStatus.FAILED, 0,
                        "未提取到内容");
                messagingTemplate.convertAndSendToUser(
                        userId.toString(),
                        "/queue/document-updates",
                        new DocumentUpdateMessage(documentId, DocumentStatus.FAILED, 0, "未提取到内容"));
                return;
            }

            // 3. 清洗 Segment 内容
            List<TextSegment> segments = rawSegments.stream()
                    .map(this::cleanSegment)
                    .filter(seg -> !seg.text().isBlank())
                    .toList();
            
            if (segments.isEmpty()) {
                 log.error("清洗后文档 {} 的所有片段均被过滤掉", documentId);
                 documentService.updateDocumentStatusAndProgress(documentId, DocumentStatus.FAILED, 0,
                         "清洗后没有剩余内容");
                 // ... 处理错误
                 return;
            }

            // 4. 生成嵌入并存储
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                // 使用Jieba进行分词，并将分词结果用空格连接
                List<SegToken> tokens = jiebaSegmenter.process(segment.text(), JiebaSegmenter.SegMode.SEARCH);
                String contentKeywords = tokens.stream()
                        .map(item -> item.word)
                        .filter(word -> !stopWords.contains(word))
                        .collect(Collectors.joining(" "));
                
                List<Embedding> embeddings = embeddingModel.embedAll(List.of(segment)).content();
                float[] embedding = embeddings.getFirst().vector();

                int currentProgress = (int) ((double) (i + 1) / segments.size() * 100);
                if (currentProgress % 10 == 0 || i == segments.size() - 1) {
                    documentService.updateDocumentStatusAndProgress(documentId, DocumentStatus.PROCESSING,
                            currentProgress, null);
                    messagingTemplate.convertAndSendToUser(
                            userId.toString(),
                            "/queue/document-updates",
                            new DocumentUpdateMessage(documentId, DocumentStatus.PROCESSING, currentProgress, null));
                    log.info("文档 {} 解析进度: {}%", documentId, currentProgress);
                }

                String metadataJson = "{}";
                try {
                    metadataJson = objectMapper.writeValueAsString(segment.metadata().toMap());
                } catch (Exception e) {
                    log.error("无法为文档 {} 序列化元数据", documentId, e);
                }

                String insertSql = """
                        INSERT INTO chunks(id, document_id, content, content_vector, chunk_index, source_meta, chunker_name, content_keywords, created_at)
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                        """;

                // 只在DEBUG级别记录SQL语句，避免生产环境输出
                if (log.isDebugEnabled()) {
                    log.debug("入库 INSERT SQL: {}", insertSql);
                }

                // 记录处理进度，避免输出敏感的文档内容
                if (i % 100 == 0 || i == segments.size() - 1) {
                    int progress = (i + 1) * 100 / segments.size();
                    log.info("文档 {} 入库进度: {}/{} ({}%), 当前块长度: {} 字符",
                            documentId, i + 1, segments.size(), progress, segment.text().length());
                }

                jdbcClient.sql(insertSql)
                        .params(
                                UUID.randomUUID(),
                                documentId,
                                segment.text().replaceAll("\u0000", ""),
                                new PGvector(embedding),
                                i,
                                metadataJson,
                                chunker.getClass().getSimpleName(),
                                contentKeywords,
                                OffsetDateTime.now())
                        .update();
            }

            documentService.updateDocumentStatusAndProgress(documentId, DocumentStatus.COMPLETED, 100, null);
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/document-updates",
                    new DocumentUpdateMessage(documentId, DocumentStatus.COMPLETED, 100, null));

        } catch (Exception e) {
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
            documentService.updateDocumentStatusAndProgress(documentId, DocumentStatus.FAILED, 0, errorMessage);
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/document-updates",
                    new DocumentUpdateMessage(documentId, DocumentStatus.FAILED, 0, errorMessage));
        } finally {
            try {
                Files.deleteIfExists(tempFilePath);
            } catch (IOException e) {
                log.error("删除临时文件 {} 失败: {}", tempFilePath, e.getMessage(), e);
            }
        }
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

    // Removed parseDocument and cleanDocument methods


}