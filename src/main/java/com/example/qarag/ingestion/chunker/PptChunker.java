package com.example.qarag.ingestion.chunker;

import com.example.qarag.config.RagProperties;
import com.example.qarag.ingestion.vision.VisionService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PPT 文档切分器
 * 支持 .ppt 和 .pptx 格式
 * 针对 .pptx 格式进行幻灯片维度的结构化解析（文本、备注、图片）
 * .ppt 格式使用回退策略（纯文本提取）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PptChunker implements DocumentChunker {

    private final RagProperties ragProperties;
    private final VisionService visionService;

    @Override
    public List<TextSegment> chunk(Path filePath) {
        log.debug("对文档使用 PptChunker: {}", filePath.getFileName());

        String filename = filePath.getFileName().toString().toLowerCase();

        if (filename.endsWith(".pptx")) {
            try {
                return processPptx(filePath);
            } catch (Exception e) {
                log.error("结构化处理 .pptx 失败，回退到纯文本提取: {}", e.getMessage(), e);
            }
        }

        return processLegacyOrFallback(filePath);
    }

    private List<TextSegment> processPptx(Path filePath) throws IOException {
        List<TextSegment> segments = new ArrayList<>();

        try (InputStream is = new FileInputStream(filePath.toFile());
             XMLSlideShow ppt = new XMLSlideShow(is)) {

            DocumentSplitter recursiveSplitter = DocumentSplitters.recursive(
                    ragProperties.chunking().size(),
                    ragProperties.chunking().overlap());

            List<XSLFSlide> slides = ppt.getSlides();
            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                int slideNumber = i + 1;
                
                StringBuilder slideContent = new StringBuilder();
                slideContent.append("## Slide ").append(slideNumber).append("\n\n");

                // 1. 提取幻灯片中的文本
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            slideContent.append(text).append("\n");
                        }
                    }
                }

                // 2. 提取备注 (Notes)
                XSLFNotes notes = slide.getNotes();
                if (notes != null) {
                    for (XSLFShape shape : notes.getShapes()) {
                        if (shape instanceof XSLFTextShape) {
                            String noteText = ((XSLFTextShape) shape).getText();
                            if (noteText != null && !noteText.isBlank()) {
                                slideContent.append("\n> **备注**: ").append(noteText).append("\n");
                            }
                        }
                    }
                }

                // 3. 提取图片并分析
                if (visionService.isEnabled()) {
                    for (XSLFShape shape : slide.getShapes()) {
                        if (shape instanceof XSLFPictureShape pictureShape) {
                            XSLFPictureData pictureData = pictureShape.getPictureData();
                            try {
                                byte[] data = pictureData.getData();
                                BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                                if (image != null) {
                                    String desc = visionService.analyzeImage(image, "请描述这张幻灯片中的图片内容。如果是图表，请尝试总结其核心趋势或数据。");
                                    slideContent.append("\n> **[图片分析]** ").append(desc).append("\n");
                                }
                            } catch (Exception e) {
                                log.warn("分析 PPT 幻灯片 {} 中的图片失败: {}", slideNumber, e.getMessage());
                            }
                        }
                    }
                }

                // 创建切片
                Metadata metadata = Metadata.from("source", filePath.getFileName().toString())
                        .put("type", "ppt")
                        .put("slide_number", slideNumber);

                String content = slideContent.toString().trim();
                if (!content.isEmpty()) {
                    if (content.length() <= ragProperties.chunking().size()) {
                        segments.add(TextSegment.from(content, metadata));
                    } else {
                        List<TextSegment> subSegments = recursiveSplitter.split(Document.from(content));
                        for (TextSegment sub : subSegments) {
                            segments.add(TextSegment.from(sub.text(), metadata));
                        }
                    }
                }
            }
        }
        return segments;
    }

    private List<TextSegment> processLegacyOrFallback(Path filePath) {
        Document document;
        try (InputStream is = new FileInputStream(filePath.toFile())) {
            document = new ApachePoiDocumentParser().parse(is);
        } catch (IOException e) {
            log.error("解析 PPT 文件失败: {}", filePath, e);
            throw new RuntimeException("解析 PPT 文件失败", e);
        }

        DocumentSplitter splitter = DocumentSplitters.recursive(
                ragProperties.chunking().size(),
                ragProperties.chunking().overlap());

        List<TextSegment> segments = splitter.split(document);

        return segments.stream()
                .map(seg -> TextSegment.from(
                        seg.text(),
                        Metadata.from("source", filePath.getFileName().toString())
                                .put("type", "ppt")))
                .toList();
    }

    @Override
    public boolean supports(String filename) {
        if (filename == null)
            return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".ppt") || lower.endsWith(".pptx");
    }

    @Override
    public int priority() {
        return 20;
    }
}
