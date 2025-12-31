package com.twocold.jrag.ingestion.chunker.pdf;

/**
 * PDF 页面元素类型
 */
public enum PdfElementType {
    /**
     * 普通文本
     */
    TEXT,

    /**
     * 文本型表格 (可用 Tabula 提取)
     */
    TEXT_TABLE,

    /**
     * 图片型表格 (需要 OCR 或 视觉模型)
     */
    IMAGE_TABLE,

    /**
     * 统计图表 (柱状图、饼图等，需要 视觉模型 理解)
     */
    CHART,

    /**
     * 扫描件页面 (整页是图片，需要 OCR)
     */
    SCANNED_PAGE,

    /**
     * 普通图片
     */
    IMAGE
}
