package com.twocold.jrag.ingestion.chunker.pdf;

import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * PDF 元素处理结果
 */
public record PdfElementResult(
        /*
          处理后的文本内容
         */
        String content,

        /*
         * 元素类型
         */
        PdfElementType elementType,

        /*
         * 页码 (1-indexed)
         */
        int pageNumber,

        /*
         * 是否处理成功
         */
        boolean success,

        /*
         * 错误信息 (如果失败)
         */
        String errorMessage,

        /*
         * 处理过的区域列表 (用于后续处理器跳过)
         * 坐标系: PDF 坐标系 (左下角为原点)
         */
        List<Rectangle2D> processedRegions) {
    /**
     * 创建成功结果 (无区域信息)
     */
    public static PdfElementResult success(String content, PdfElementType type, int pageNumber) {
        return new PdfElementResult(content, type, pageNumber, true, null, List.of());
    }

    /**
     * 创建成功结果 (带区域信息)
     */
    public static PdfElementResult success(String content, PdfElementType type, int pageNumber,
            List<Rectangle2D> processedRegions) {
        return new PdfElementResult(content, type, pageNumber, true, null, processedRegions);
    }

    /**
     * 创建失败结果
     */
    public static PdfElementResult failure(PdfElementType type, int pageNumber, String errorMessage) {
        return new PdfElementResult(null, type, pageNumber, false, errorMessage, List.of());
    }

    /**
     * 创建空结果 (元素无内容)
     */
    public static PdfElementResult empty(PdfElementType type, int pageNumber) {
        return new PdfElementResult("", type, pageNumber, true, null, List.of());
    }
}
