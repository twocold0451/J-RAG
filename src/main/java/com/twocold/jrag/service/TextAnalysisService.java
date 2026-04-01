package com.twocold.jrag.service;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文本分析服务，负责关键词提取和向量生成
 * 从 IngestionServiceImpl 中提取，供文档入库和 Chunk 管理复用
 */
@Slf4j
@Service
public class TextAnalysisService {

    private final EmbeddingModel embeddingModel;
    private final JiebaSegmenter jiebaSegmenter = new JiebaSegmenter();
    private final Set<String> stopWords = new HashSet<>();

    public TextAnalysisService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

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
                log.info("TextAnalysisService: 已加载 {} 个停用词。", stopWords.size());
            } else {
                log.error("TextAnalysisService: 未找到 stopwords.txt。");
            }
        } catch (Exception e) {
            log.error("TextAnalysisService: Failed to load stop words", e);
        }
    }

    /**
     * 提取文本关键词（使用 Jieba 分词并过滤停用词）
     *
     * @param text 输入文本
     * @return 空格分隔的关键词字符串
     */
    public String extractKeywords(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        List<SegToken> tokens = jiebaSegmenter.process(text, JiebaSegmenter.SegMode.SEARCH);
        return tokens.stream()
                .map(item -> item.word)
                .filter(word -> !stopWords.contains(word))
                .collect(Collectors.joining(" "));
    }

    /**
     * 生成文本的 Embedding 向量
     *
     * @param text 输入文本
     * @return Embedding 向量
     */
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        return embeddingModel.embedAll(List.of(TextSegment.from(text)))
                .content().getFirst().vector();
    }

    /**
     * 批量生成文本的 Embedding 向量
     *
     * @param texts 输入文本列表
     * @return Embedding 向量列表
     */
    public List<float[]> generateEmbeddings(List<String> texts) {
        List<TextSegment> segments = texts.stream()
                .map(TextSegment::from)
                .toList();
        List<Embedding> embeddings = embedAllWithRetry(segments);
        return embeddings.stream()
                .map(Embedding::vector)
                .toList();
    }

    /**
     * 分析文本，同时生成关键词和向量
     *
     * @param text 输入文本
     * @return AnalysisResult 包含关键词和向量
     */
    public AnalysisResult analyze(String text) {
        String keywords = extractKeywords(text);
        float[] embedding = generateEmbedding(text);
        return new AnalysisResult(keywords, embedding);
    }

    /**
     * 带重试机制的批量 Embedding 调用
     */
    private List<Embedding> embedAllWithRetry(List<TextSegment> segments) {
        int maxRetries = 3;
        long waitTime = 2000; // 初始等待 2 秒

        for (int i = 0; i < maxRetries; i++) {
            try {
                return embeddingModel.embedAll(segments).content();
            } catch (RuntimeException e) {
                if (i == maxRetries - 1) {
                    throw e;
                }

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
                    waitTime *= 2;
                } else {
                    throw e;
                }
            }
        }
        throw new RuntimeException("Embedding 重试失败");
    }

    /**
     * 分析结果封装
     */
    public record AnalysisResult(String keywords, float[] embedding) {
    }
}
