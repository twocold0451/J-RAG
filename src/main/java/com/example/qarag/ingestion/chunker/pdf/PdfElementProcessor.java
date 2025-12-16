package com.example.qarag.ingestion.chunker.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * PDF 元素处理器接口
 * 不同类型的 PDF 元素使用不同的处理策略
 */
public interface PdfElementProcessor {

    /**
     * 获取处理器支持的元素类型
     *
     * @return 支持的元素类型
     */
    PdfElementType supportedType();

    /**
     * 处理 PDF 页面中的特定元素
     *
     * @param document       PDF 文档对象
     * @param pageNumber     页码 (1-indexed)
     * @param excludeRegions 需要跳过的区域 (已被其他处理器处理)
     * @return 处理结果，包含处理的内容和处理过的区域
     */
    PdfElementResult process(PDDocument document, int pageNumber, List<Rectangle2D> excludeRegions);

    /**
     * 检测页面是否包含此处理器能处理的元素
     *
     * @param document   PDF 文档对象
     * @param pageNumber 页码 (1-indexed)
     * @return 如果包含返回 true
     */
    boolean detect(PDDocument document, int pageNumber);

    /**
     * 获取优先级，数值越小优先级越高
     * 当多个处理器都能处理同一页面时，使用优先级最高的
     *
     * @return 优先级数值
     */
    default int priority() {
        return 100;
    }
}
