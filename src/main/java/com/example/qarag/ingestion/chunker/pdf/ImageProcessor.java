package com.example.qarag.ingestion.chunker.pdf;

import com.example.qarag.ingestion.vision.VisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 * 图片/图表处理器
 * 处理 PDF 页面中包含的图片和图表，使用 视觉模型 理解其内容
 * 适用于：有文本也有图片的页面（非纯扫描件）
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class ImageProcessor implements PdfElementProcessor {

    private final VisionService visionService;

    private static final String CHART_ANALYSIS_PROMPT = """
            请分析这个页面中的图片和图表内容：

            1. 如果是数据图表（柱状图、饼图、折线图等）：
               - 描述图表类型和主题
               - 提取关键数据点和趋势
               - 用文字描述图表所呈现的信息

            2. 如果是表格图片：
               - 用 Markdown 表格格式输出内容
               - 保持数字的准确性

            3. 如果是普通图片：
               - 简要描述图片内容

            4. 如果是流程图或组织架构图：
               - 描述结构和关系

            注意：只关注图片和图表内容，页面中的普通文字不需要提取。
            直接输出分析结果，不要添加额外说明。
            """;

    @Override
    public PdfElementType supportedType() {
        return PdfElementType.IMAGE;
    }

    @Override
    public PdfElementResult process(PDDocument document, int pageNumber, List<Rectangle2D> excludeRegions) {
        // 检查 Vision 服务是否可用
        if (!visionService.isEnabled()) {
            log.info("Vision service not enabled, skipping image processing for page {}", pageNumber);
            return PdfElementResult.empty(PdfElementType.IMAGE, pageNumber);
        }

        try {
            PDPage page = document.getPage(pageNumber - 1);
            PDResources resources = page.getResources();

            // 统计图片数量
            int imageCount = 0;
            for (COSName name : resources.getXObjectNames()) {
                if (resources.getXObject(name) instanceof PDImageXObject) {
                    imageCount++;
                }
            }

            if (imageCount == 0) {
                return PdfElementResult.empty(PdfElementType.IMAGE, pageNumber);
            }

            log.info("Processing {} images on page {} with Vision API", imageCount, pageNumber);

            // 渲染整页为图片发送给 视觉模型
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage pageImage = renderer.renderImageWithDPI(pageNumber - 1, 150);

            long imageSizeBytes = (long) pageImage.getWidth() * pageImage.getHeight() * 3; // Approx 3 bytes per pixel (RGB)
            log.info("Rendered page {} as image: {}x{} pixels, approx size in memory: {} KB", 
                     pageNumber, pageImage.getWidth(), pageImage.getHeight(), imageSizeBytes / 1024);

            // 调用 视觉模型 分析图表
            long start = System.currentTimeMillis();
            String analysisResult = visionService.analyzeImage(pageImage, CHART_ANALYSIS_PROMPT);
            long duration = System.currentTimeMillis() - start;
            
            if (duration > 5000) {
                 log.warn("Vision analysis for page {} took {} ms ( > 5s )", pageNumber, duration);
            } else {
                 log.info("Vision analysis for page {} took {} ms", pageNumber, duration);
            }

            if (analysisResult == null || analysisResult.isBlank()
                    || analysisResult.contains("未启用") || analysisResult.contains("失败")) {
                log.warn("Vision API returned empty or error for page {}", pageNumber);
                return PdfElementResult.empty(PdfElementType.IMAGE, pageNumber);
            }
            
            // Log the vision model's analysis result (limit length for readability)
            String loggableResult = analysisResult.length() > 500 
                                  ? analysisResult.substring(0, 500) + "... [truncated]"
                                  : analysisResult;
            log.info("Vision Model Analysis Result for page {}: \n{}", pageNumber, loggableResult.replace("\n", " "));

            log.info("Successfully analyzed {} images on page {}, got {} characters",
                    imageCount, pageNumber, analysisResult.length());

            return PdfElementResult.success(
                    analysisResult,
                    PdfElementType.IMAGE,
                    pageNumber);

        } catch (IOException e) {
            log.error("Failed to process images on page {}: {}", pageNumber, e.getMessage());
            return PdfElementResult.failure(PdfElementType.IMAGE, pageNumber, e.getMessage());
        }
    }

    @Override
    public boolean detect(PDDocument document, int pageNumber) {
        try {
            PDPage page = document.getPage(pageNumber - 1);
            PDResources resources = page.getResources();

            // 检查是否有图片
            boolean hasImages = false;
            for (COSName name : resources.getXObjectNames()) {
                if (resources.getXObject(name) instanceof PDImageXObject) {
                    hasImages = true;
                    break;
                }
            }

            if (!hasImages) {
                return false;
            }

            // 检查是否有文本 (区分纯扫描件和混合页面)
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageNumber);
            stripper.setEndPage(pageNumber);
            String text = stripper.getText(document).trim();

            // 如果有文本也有图片，由 ImageProcessor 处理图片部分
            // (纯扫描件 = 无文本有图片，由 ScannedPageProcessor 处理)
            boolean shouldProcess = !text.isEmpty();

            if (shouldProcess) {
                log.debug("Page {} has both text and images, ImageProcessor will handle images", pageNumber);
            }

            return shouldProcess;

        } catch (Exception e) {
            log.debug("Failed to detect images on page {}: {}", pageNumber, e.getMessage());
            return false;
        }
    }

    @Override
    public int priority() {
        return 20;
    }
}
