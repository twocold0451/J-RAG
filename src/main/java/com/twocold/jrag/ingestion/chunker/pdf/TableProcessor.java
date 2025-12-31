package com.twocold.jrag.ingestion.chunker.pdf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * 文本型表格处理器
 * 使用 Tabula 提取 PDF 中的表格并转换为 Markdown 格式
 * 返回表格区域坐标，供后续处理器跳过
 * 注意：不能使用 try-with-resources 关闭 ObjectExtractor，
 * 因为它会关闭底层的 PDDocument
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class TableProcessor implements PdfElementProcessor {

    private final SpreadsheetExtractionAlgorithm tableExtractor = new SpreadsheetExtractionAlgorithm();

    @Override
    public PdfElementType supportedType() {
        return PdfElementType.TEXT_TABLE;
    }

    @Override
    public PdfElementResult process(PDDocument document, int pageNumber, List<Rectangle2D> excludeRegions) {
        try {
            // 注意：不使用 try-with-resources，因为 ObjectExtractor.close() 会关闭 PDDocument
            ObjectExtractor extractor = new ObjectExtractor(document);
            Page page = extractor.extract(pageNumber);
            List<Table> tables = tableExtractor.extract(page);

            if (tables.isEmpty()) {
                return PdfElementResult.empty(PdfElementType.TEXT_TABLE, pageNumber);
            }

            StringBuilder content = new StringBuilder();
            List<Rectangle2D> tableRegions = new ArrayList<>();

            for (int i = 0; i < tables.size(); i++) {
                Table table = tables.get(i);

                // 记录表格区域
                Rectangle2D tableRect = new Rectangle2D.Float(
                        (float) table.getX(),
                        (float) table.getY(),
                        (float) table.getWidth(),
                        (float) table.getHeight());
                tableRegions.add(tableRect);

                if (i > 0) {
                    content.append("\n\n");
                }
                String markdown = convertTableToMarkdown(table);
                content.append(markdown);
            }

            log.debug("从第 {} 页提取了 {} 个表格，区域: {}",
                    tables.size(), pageNumber, tableRegions);
            return PdfElementResult.success(content.toString(), PdfElementType.TEXT_TABLE,
                    pageNumber, tableRegions);

        } catch (Exception e) {
            log.error("提取第 {} 页的表格失败: {}", pageNumber, e.getMessage());
            return PdfElementResult.failure(PdfElementType.TEXT_TABLE, pageNumber, e.getMessage());
        }
    }

    @Override
    public boolean detect(PDDocument document, int pageNumber) {
        try {
            // 注意：不使用 try-with-resources
            ObjectExtractor extractor = new ObjectExtractor(document);
            Page page = extractor.extract(pageNumber);
            List<Table> tables = tableExtractor.extract(page);
            return !tables.isEmpty();
        } catch (Exception e) {
            log.debug("Failed to detect tables on page {}: {}", pageNumber, e.getMessage());
            return false;
        }
    }

    @Override
    public int priority() {
        return 10;
    }

    @SuppressWarnings("rawtypes")
    private String convertTableToMarkdown(Table table) {
        List<List<RectangularTextContainer>> rows = table.getRows();
        if (rows.isEmpty()) {
            return "";
        }

        StringBuilder markdown = new StringBuilder();

        for (int i = 0; i < rows.size(); i++) {
            List<RectangularTextContainer> row = rows.get(i);
            StringBuilder rowBuilder = new StringBuilder("|");

            for (RectangularTextContainer cell : row) {
                String cellText = cell.getText()
                        .replace("\r", " ")
                        .replace("\n", " ")
                        .replace("|", "\\|")
                        .trim();
                rowBuilder.append(" ").append(cellText).append(" |");
            }

            markdown.append(rowBuilder).append("\n");

            if (i == 0) {
                markdown.append("|").append(" --- |".repeat(row.size())).append("\n");
            }
        }

        return markdown.toString();
    }
}
