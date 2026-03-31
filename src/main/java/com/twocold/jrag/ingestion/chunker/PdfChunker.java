package com.twocold.jrag.ingestion.chunker;

import com.twocold.jrag.config.RagProperties;
import com.twocold.jrag.ingestion.chunker.pdf.PdfElementProcessorFactory;
import com.twocold.jrag.ingestion.chunker.pdf.PdfElementResult;
import com.twocold.jrag.ingestion.utils.TextCleaner;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PDF 文档切分器
 * 使用嵌套策略模式，根据页面内容调用不同的元素处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfChunker implements DocumentChunker {

    private final RagProperties ragProperties;
    private final PdfElementProcessorFactory processorFactory;

    @Override
    public List<TextSegment> chunk(Path filePath) {
        return chunk(filePath, null);
    }

    @Override
    public List<TextSegment> chunk(Path filePath, java.util.function.BiConsumer<Integer, Integer> progressCallback) {
        log.debug("对 PDF 文档使用 PdfChunker: {}", filePath.getFileName());

        List<TextSegment> segments = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        // 优化：使用临时文件缓冲，减少内存占用，特适合大文件
        try (PDDocument pdfDoc = PDDocument.load(filePath.toFile(), MemoryUsageSetting.setupTempFileOnly())) {
            
            // 检查文档是否加密
            if (pdfDoc.isEncrypted()) {
                log.info("PDF 已加密: {}", filePath.getFileName());
                AccessPermission ap = pdfDoc.getCurrentAccessPermission();
                if (!ap.canExtractContent()) {
                    log.error("没有提取权限，无法处理加密的 PDF: {}", filePath.getFileName());
                    throw new IOException("PDF 已加密且禁用了内容提取");
                }
            }
            
            int totalPages = pdfDoc.getNumberOfPages();
            log.info("PDF 共有 {} 页，正在使用元素处理器进行处理", totalPages);

            int overlapSize = ragProperties.chunking().overlap();
            String previousPageOverlap = "";

            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                // 使用处理器工厂处理页面
                List<PdfElementResult> results = processorFactory.processPage(pdfDoc, pageNum);

                // 合并所有处理结果
                String pageContent = results.stream()
                        .filter(r -> r.success() && r.content() != null && !r.content().isBlank())
                        .map(PdfElementResult::content)
                        .collect(Collectors.joining("\n\n"));

                // 文本清洗
                pageContent = TextCleaner.clean(pageContent);

                if (pageContent.isBlank()) {
                    log.debug("Page {} has no extractable content, skipping", pageNum);
                } else {
                    // 记录处理的元素类型 (去重)
                    String elementTypes = results.stream()
                            .filter(r -> r.success() && r.content() != null && !r.content().isBlank())
                            .map(r -> r.elementType().name())
                            .distinct()
                            .collect(Collectors.joining(", "));
                    
                    log.debug("第 {} 页处理的元素类型: {}", pageNum, elementTypes);

                    // 拼接上一页的 overlap
                    String chunkContent = previousPageOverlap.isEmpty() ? pageContent : previousPageOverlap + "\n" + pageContent;

                    TextSegment segment = TextSegment.from(
                            chunkContent,
                            Metadata.from("page", String.valueOf(pageNum))
                                    .put("source", filePath.getFileName().toString())
                                    .put("elements", elementTypes));
                    segments.add(segment);

                    // 更新上一页的 overlap，供下一页使用
                    if (chunkContent.length() > overlapSize) {
                        // 从后往前截取 overlapSize，并尽量找到最近的空白字符避免截断单词
                        int sliceIndex = chunkContent.length() - overlapSize;
                        int spaceIndex = -1;
                        for (int i = sliceIndex; i < chunkContent.length(); i++) {
                            char c = chunkContent.charAt(i);
                            if (Character.isWhitespace(c)) {
                                spaceIndex = i;
                                break;
                            }
                        }
                        if (spaceIndex != -1) {
                            previousPageOverlap = chunkContent.substring(spaceIndex).trim();
                        } else {
                            previousPageOverlap = chunkContent.substring(sliceIndex);
                        }
                    } else {
                        previousPageOverlap = chunkContent;
                    }
                }

                // 报告进度
                if (progressCallback != null) {
                    try {
                        progressCallback.accept(pageNum, totalPages);
                    } catch (Exception e) {
                        log.warn("进度回调执行失败", e);
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("PdfChunker 完成了对 {} 页的处理，耗时 {} 毫秒。生成了 {} 个片段。", 
                    totalPages, duration, segments.size());

        } catch (InvalidPasswordException e) {
            log.error("处理 PDF 文件失败 (需要密码): {}", filePath, e);
            throw new RuntimeException("无法处理加密的 PDF: 需要密码", e);
        } catch (IOException e) {
            log.error("处理 PDF 文件失败: {}", filePath, e);
            throw new RuntimeException("处理 PDF 文件失败", e);
        }

        return segments;
    }

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    @Override
    public int priority() {
        return 10;
    }
}
