package com.example.qarag.ingestion.chunker.pdf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * 普通文本处理器
 * 使用 PDFBox 提取 PDF 页面中的文本内容
 * 支持跳过已被其他处理器处理的区域
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class TextProcessor implements PdfElementProcessor {

    @Override
    public PdfElementType supportedType() {
        return PdfElementType.TEXT;
    }

    @Override
    public PdfElementResult process(PDDocument document, int pageNumber, List<Rectangle2D> excludeRegions) {
        try {
            PDPage page = document.getPage(pageNumber - 1);

            // 获取页面尺寸
            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();

            // 使用按区域提取的方式
            PDFTextStripperByArea stripper = new PDFTextStripperByArea();

            if (excludeRegions.isEmpty()) {
                // 无排除区域，提取整页
                Rectangle2D fullPage = new Rectangle2D.Float(0, 0, pageWidth, pageHeight);
                stripper.addRegion("fullPage", fullPage);
            } else {
                // 有排除区域，计算非排除区域并提取
                List<Rectangle2D> textRegions = calculateTextRegions(pageWidth, pageHeight, excludeRegions);

                int regionIndex = 0;
                for (Rectangle2D region : textRegions) {
                    stripper.addRegion("textRegion" + regionIndex++, region);
                }

                log.debug("第 {} 页：正在提取 {} 个文本区域，排除了 {} 个区域",
                        pageNumber, textRegions.size(), excludeRegions.size());
            }

            stripper.extractRegions(page);

            // 合并所有区域的文本
            StringBuilder textBuilder = new StringBuilder();
            for (String regionName : stripper.getRegions()) {
                String regionText = stripper.getTextForRegion(regionName).trim();
                if (!regionText.isEmpty()) {
                    if (!textBuilder.isEmpty()) {
                        textBuilder.append("\n\n");
                    }
                    textBuilder.append(regionText);
                }
            }

            String text = textBuilder.toString();
            if (text.isEmpty()) {
                return PdfElementResult.empty(PdfElementType.TEXT, pageNumber);
            }

            log.debug("从第 {} 页提取了 {} 个字符 (排除了 {} 个区域)",
                    text.length(), pageNumber, excludeRegions.size());
            return PdfElementResult.success(text, PdfElementType.TEXT, pageNumber);

        } catch (Exception e) {
            log.error("从第 {} 页提取文本失败: {}", pageNumber, e.getMessage());
            return PdfElementResult.failure(PdfElementType.TEXT, pageNumber, e.getMessage());
        }
    }

    private static final float MIN_REGION_WIDTH = 30.0f;
    private static final float MIN_REGION_HEIGHT = 10.0f;

    /**
     * 计算非排除区域
     * 计算页面区域减去排除区域后的剩余区域
     */
    private List<Rectangle2D> calculateTextRegions(float pageWidth, float pageHeight,
            List<Rectangle2D> excludeRegions) {
        if (excludeRegions == null || excludeRegions.isEmpty()) {
            return List.of(new Rectangle2D.Float(0, 0, pageWidth, pageHeight));
        }

        List<Rectangle2D> availableRegions = new ArrayList<>();
        availableRegions.add(new Rectangle2D.Float(0, 0, pageWidth, pageHeight));

        for (Rectangle2D excluded : excludeRegions) {
            List<Rectangle2D> nextRegions = new ArrayList<>();
            for (Rectangle2D region : availableRegions) {
                if (!region.intersects(excluded)) {
                    nextRegions.add(region);
                } else {
                    // 计算交集部分
                    Rectangle2D intersection = region.createIntersection(excluded);

                    if (intersection.isEmpty()) {
                        nextRegions.add(region);
                        continue;
                    }

                    // 1. 上方区域 (Top)
                    float topHeight = (float) (intersection.getMinY() - region.getMinY());
                    if (region.getMinY() < intersection.getMinY() && topHeight > MIN_REGION_HEIGHT) {
                        nextRegions.add(new Rectangle2D.Float(
                                (float) region.getX(),
                                (float) region.getY(),
                                (float) region.getWidth(),
                                topHeight
                        ));
                    }

                    // 2. 下方区域 (Bottom)
                    float bottomHeight = (float) (region.getMaxY() - intersection.getMaxY());
                    if (region.getMaxY() > intersection.getMaxY() && bottomHeight > MIN_REGION_HEIGHT) {
                        nextRegions.add(new Rectangle2D.Float(
                                (float) region.getX(),
                                (float) intersection.getMaxY(),
                                (float) region.getWidth(),
                                bottomHeight
                        ));
                    }

                    // 3. 左侧区域 (Left) - 高度限制在交集高度内
                    float leftWidth = (float) (intersection.getMinX() - region.getMinX());
                    if (region.getMinX() < intersection.getMinX() && leftWidth > MIN_REGION_WIDTH) {
                        nextRegions.add(new Rectangle2D.Float(
                                (float) region.getX(),
                                (float) intersection.getMinY(),
                                leftWidth,
                                (float) intersection.getHeight()
                        ));
                    }

                    // 4. 右侧区域 (Right) - 高度限制在交集高度内
                    float rightWidth = (float) (region.getMaxX() - intersection.getMaxX());
                    if (region.getMaxX() > intersection.getMaxX() && rightWidth > MIN_REGION_WIDTH) {
                        nextRegions.add(new Rectangle2D.Float(
                                (float) intersection.getMaxX(),
                                (float) intersection.getMinY(),
                                rightWidth,
                                (float) intersection.getHeight()
                        ));
                    }
                }
            }
            availableRegions = nextRegions;
        }

        return availableRegions;
    }

    @Override
    public boolean detect(PDDocument document, int pageNumber) {
        // 文本处理器始终可用，作为 fallback
        return true;
    }

    @Override
    public int priority() {
        return Integer.MAX_VALUE;
    }
}
