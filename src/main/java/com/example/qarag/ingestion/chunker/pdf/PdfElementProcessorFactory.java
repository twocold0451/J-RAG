package com.example.qarag.ingestion.chunker.pdf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PDF 元素处理器工厂
 * 根据页面内容自动选择合适的处理器
 * 支持互斥处理：高优先级处理器处理过的区域，低优先级处理器会跳过
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfElementProcessorFactory {

    private final List<PdfElementProcessor> processors;

    /**
     * 检测并获取页面适用的所有处理器（按优先级排序）
     */
    public List<PdfElementProcessor> getProcessorsForPage(PDDocument document, int pageNumber) {
        return processors.stream()
                .filter(p -> p.detect(document, pageNumber))
                .sorted(Comparator.comparingInt(PdfElementProcessor::priority))
                .collect(Collectors.toList());
    }

    /**
     * 获取指定类型的处理器
     */
    public PdfElementProcessor getProcessor(PdfElementType type) {
        return processors.stream()
                .filter(p -> p.supportedType() == type)
                .findFirst()
                .orElse(null);
    }

    /**
     * 处理页面中的所有元素（支持互斥处理）
     * 高优先级处理器返回的区域会传递给低优先级处理器跳过
     */
    public List<PdfElementResult> processPage(PDDocument document, int pageNumber) {
        List<PdfElementProcessor> applicableProcessors = getProcessorsForPage(document, pageNumber);

        if (applicableProcessors.isEmpty()) {
            log.warn("No processor found for page {}", pageNumber);
            return List.of();
        }

        log.debug("Found {} processors for page {}: {}",
                applicableProcessors.size(),
                pageNumber,
                applicableProcessors.stream()
                        .map(p -> p.supportedType().name())
                        .collect(Collectors.joining(", ")));

        List<PdfElementResult> results = new ArrayList<>();
        List<Rectangle2D> excludeRegions = new ArrayList<>();

        // 按优先级依次处理，传递已处理的区域
        for (PdfElementProcessor processor : applicableProcessors) {
            try {
                log.debug("Processing page {} with {} (excluding {} regions)",
                        pageNumber, processor.supportedType(), excludeRegions.size());

                PdfElementResult result = processor.process(document, pageNumber, excludeRegions);

                if (result.success() && result.content() != null && !result.content().isBlank()) {
                    results.add(result);

                    // 将此处理器处理过的区域加入排除列表
                    if (result.processedRegions() != null && !result.processedRegions().isEmpty()) {
                        excludeRegions.addAll(result.processedRegions());
                        log.debug("Added {} regions to exclude list from {}",
                                result.processedRegions().size(), processor.supportedType());
                    }
                }
            } catch (Exception e) {
                log.error("Processor {} failed for page {}: {}",
                        processor.supportedType(), pageNumber, e.getMessage());
                results.add(PdfElementResult.failure(processor.supportedType(), pageNumber, e.getMessage()));
            }
        }

        return results;
    }
}
