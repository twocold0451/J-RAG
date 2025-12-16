package com.example.qarag.ingestion.chunker;

import com.example.qarag.config.RagProperties;
import com.example.qarag.ingestion.chunker.pdf.PdfElementProcessorFactory;
import com.example.qarag.ingestion.chunker.pdf.PdfElementResult;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
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
        log.debug("Using PdfChunker for PDF document: {}", filePath.getFileName());

        List<TextSegment> segments = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        // 优化：使用临时文件缓冲，减少内存占用，特适合大文件
        try (PDDocument pdfDoc = PDDocument.load(filePath.toFile(), MemoryUsageSetting.setupTempFileOnly())) {
            
            // 检查文档是否加密
            if (pdfDoc.isEncrypted()) {
                log.info("PDF is encrypted: {}", filePath.getFileName());
                AccessPermission ap = pdfDoc.getCurrentAccessPermission();
                if (!ap.canExtractContent()) {
                    log.warn("Cannot process encrypted PDF without content extraction permission: {}", filePath.getFileName());
                    throw new IOException("PDF is encrypted and content extraction is disabled");
                }
            }
            
            int totalPages = pdfDoc.getNumberOfPages();
            log.info("PDF has {} pages, processing with element processors", totalPages);

            DocumentSplitter splitter = DocumentSplitters.recursive(
                    ragProperties.chunking().size(),
                    ragProperties.chunking().overlap());

            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                // 使用处理器工厂处理页面
                List<PdfElementResult> results = processorFactory.processPage(pdfDoc, pageNum);

                // 合并所有处理结果
                String pageContent = results.stream()
                        .filter(r -> r.success() && r.content() != null && !r.content().isBlank())
                        .map(PdfElementResult::content)
                        .collect(Collectors.joining("\n\n"));

                if (pageContent.isBlank()) {
                    log.debug("Page {} has no extractable content, skipping", pageNum);
                    continue;
                }

                // 记录处理的元素类型 (去重)
                String elementTypes = results.stream()
                        .filter(r -> r.success() && r.content() != null && !r.content().isBlank())
                        .map(r -> r.elementType().name())
                        .distinct()
                        .collect(Collectors.joining(", "));
                
                log.debug("Page {} processed elements: {}", pageNum, elementTypes);

                // 如果单页内容超过 chunk size，进行二次切分
                if (pageContent.length() > ragProperties.chunking().size()) {
                    Document pageDoc = Document.from(pageContent);
                    List<TextSegment> pageSegments = splitter.split(pageDoc);

                    for (TextSegment seg : pageSegments) {
                        TextSegment segmentWithMeta = TextSegment.from(
                                seg.text(),
                                Metadata.from("page", String.valueOf(pageNum))
                                        .put("source", filePath.getFileName().toString())
                                        .put("elements", elementTypes));
                        segments.add(segmentWithMeta);
                    }
                } else {
                    TextSegment segment = TextSegment.from(
                            pageContent,
                            Metadata.from("page", String.valueOf(pageNum))
                                    .put("source", filePath.getFileName().toString())
                                    .put("elements", elementTypes));
                    segments.add(segment);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("PdfChunker finished processing {} pages in {} ms. Produced {} segments.", 
                    totalPages, duration, segments.size());

        } catch (InvalidPasswordException e) {
            log.error("Failed to process PDF file (Password Required): {}", filePath, e);
            throw new RuntimeException("Cannot process encrypted PDF: Password required", e);
        } catch (IOException e) {
            log.error("Failed to process PDF file: {}", filePath, e);
            throw new RuntimeException("Failed to process PDF file", e);
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
