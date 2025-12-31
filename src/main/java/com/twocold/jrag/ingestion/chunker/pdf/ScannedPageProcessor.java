package com.twocold.jrag.ingestion.chunker.pdf;

import com.twocold.jrag.ingestion.vision.VisionService;
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
import java.util.List;

/**
 * 扫描件页面处理器
 * 检测整页为图片的扫描件，使用 视觉模型 进行 OCR/理解
 */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class ScannedPageProcessor implements PdfElementProcessor {

    private final VisionService visionService;

    private static final String OCR_PROMPT = """
            请仔细分析这个扫描文档页面，并按以下要求提取内容：

            1. 提取所有可见的文字内容，保持原有的段落结构
            2. 如果有表格，请用 Markdown 表格格式输出
            3. 如果有图表，请描述图表的主要内容和数据
            4. 忽略页眉、页脚和页码
            5. 如果是财务报表，请特别注意数字的准确性

            直接输出提取的内容，不要添加额外说明。
            """;

    @Override
    public PdfElementType supportedType() {
        return PdfElementType.SCANNED_PAGE;
    }

    @Override
    public PdfElementResult process(PDDocument document, int pageNumber, List<Rectangle2D> excludeRegions) {
        PDPage page = document.getPage(pageNumber - 1);
        Rectangle2D fullPage = new Rectangle2D.Float(
                0, 0,
                page.getMediaBox().getWidth(),
                page.getMediaBox().getHeight());

        // 检查 Vision 服务是否可用
        if (!visionService.isEnabled()) {
            log.info("视觉服务未启用，为扫描页第 {} 页返回占位符", pageNumber);
            String placeholder = String.format(
                    "[扫描件页面 - 页码: %d - Vision 服务未启用，请配置 app.rag.vision]",
                    pageNumber);
            return PdfElementResult.success(placeholder, PdfElementType.SCANNED_PAGE,
                    pageNumber, List.of(fullPage));
        }

        try {
            // 将 PDF 页面渲染为图片
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage pageImage = renderer.renderImageWithDPI(pageNumber - 1, 150); // 150 DPI

            log.info("正在使用 Vision API 处理扫描页第 {} 页", pageNumber);

            // 调用 视觉模型 进行 OCR
            String extractedText = visionService.analyzeImage(pageImage, OCR_PROMPT);

            if (extractedText == null || extractedText.isBlank()) {
                return PdfElementResult.empty(PdfElementType.SCANNED_PAGE, pageNumber);
            }

            log.info("成功从扫描页第 {} 页提取了 {} 个字符",
                    extractedText.length(), pageNumber);
            return PdfElementResult.success(extractedText, PdfElementType.SCANNED_PAGE,
                    pageNumber, List.of(fullPage));

        } catch (Exception e) {
            log.error("处理扫描页第 {} 页失败: {}", pageNumber, e.getMessage());
            return PdfElementResult.failure(PdfElementType.SCANNED_PAGE, pageNumber, e.getMessage());
        }
    }

    @Override
    public boolean detect(PDDocument document, int pageNumber) {
        try {
            PDPage page = document.getPage(pageNumber - 1);

            // 检查页面是否有文本
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageNumber);
            stripper.setEndPage(pageNumber);
            String text = stripper.getText(document).trim();

            // 检查页面是否有图片
            PDResources resources = page.getResources();
            boolean hasImages = false;

            for (COSName name : resources.getXObjectNames()) {
                if (resources.getXObject(name) instanceof PDImageXObject) {
                    hasImages = true;
                    break;
                }
            }

            // 如果没有文本但有图片，判定为扫描件
            boolean isScanned = text.isEmpty() && hasImages;

            if (isScanned) {
                log.debug("检测到第 {} 页为扫描件 (无文本，有图片)", pageNumber);
            }

            return isScanned;

        } catch (Exception e) {
            log.debug("Failed to detect scanned page {}: {}", pageNumber, e.getMessage());
            return false;
        }
    }

    @Override
    public int priority() {
        return 5;
    }
}
