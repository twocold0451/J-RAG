package com.example.qarag.ingestion.chunker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 文档切分器工厂
 * 根据文件名自动选择合适的切分策略
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentChunkerFactory {

    private final List<DocumentChunker> chunkers;

    /**
     * 根据文件名获取合适的 Chunker
     * 按优先级排序，返回第一个支持该文件的 Chunker
     *
     * @param filename 文件名（包含扩展名）
     * @return 适合该文件类型的 Chunker
     * @throws IllegalStateException 如果没有找到合适的 Chunker
     */
    public DocumentChunker getChunker(String filename) {
        DocumentChunker selected = chunkers.stream()
                .sorted(Comparator.comparingInt(DocumentChunker::priority))
                .filter(c -> c.supports(filename))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No chunker found for: " + filename));

        log.info("Selected chunker [{}] for file: {}", selected.getClass().getSimpleName(), filename);
        return selected;
    }
}
