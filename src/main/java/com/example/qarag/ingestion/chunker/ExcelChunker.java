package com.example.qarag.ingestion.chunker;

import com.example.qarag.config.RagProperties;
import com.example.qarag.ingestion.utils.TextCleaner;
import com.example.qarag.ingestion.vision.VisionService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 文档切分器
 * 支持 .xls 和 .xlsx 格式
 * 独立处理每个 Sheet，并转换为 Markdown 表格格式
 * 采用"每Chunk带表头"的切分策略，防止长表格切分后丢失列名信息
 * 支持使用视觉模型提取和分析 Excel 中的图片/图表
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelChunker implements DocumentChunker {

    private final RagProperties ragProperties;
    private final VisionService visionService;

    @Override
    public List<TextSegment> chunk(Path filePath) {
        log.debug("Using ExcelChunker for document: {}", filePath.getFileName());
        List<TextSegment> allSegments = new ArrayList<>();

        try (InputStream is = new FileInputStream(filePath.toFile());
             Workbook workbook = WorkbookFactory.create(is)) {

            int maxChunkSize = ragProperties.chunking().size();

            for (Sheet sheet : workbook) {
                String sheetName = sheet.getSheetName();
                if (workbook.isSheetHidden(workbook.getSheetIndex(sheet))) {
                    continue;
                }
                log.debug("Processing sheet: {}", sheetName);
                
                List<TextSegment> sheetSegments = processSheet(sheet, sheetName, filePath.getFileName().toString(), maxChunkSize);
                allSegments.addAll(sheetSegments);
            }

        } catch (Exception e) {
            log.error("处理 Excel 文件失败: {}", filePath, e);
            throw new RuntimeException("处理 Excel 文件失败", e);
        }

        return allSegments;
    }

    private List<TextSegment> processSheet(Sheet sheet, String sheetName, String sourceName, int maxChunkSize) {
        List<TextSegment> segments = new ArrayList<>();
        Map<Integer, List<String>> imageDescriptions = extractImages(sheet);
        
        int firstRowNum = sheet.getFirstRowNum();
        int lastRowNum = sheet.getLastRowNum();
        // 如果没有数据行且没有图片，直接返回
        if (lastRowNum < firstRowNum && imageDescriptions.isEmpty()) return segments;

        // 1. 构建表头 (假设第一行存在且有效)
        String header = "";
        int maxCols = 0;
        
        // 预扫描计算最大列数
        for (int i = firstRowNum; i <= lastRowNum; i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                maxCols = Math.max(maxCols, row.getLastCellNum());
            }
        }
        
        // 如果全是空行且无图片，返回
        if (maxCols == 0 && imageDescriptions.isEmpty()) return segments;

        // 构建表头行和分隔线行
        if (maxCols > 0) {
            Row headerRow = sheet.getRow(firstRowNum);
            StringBuilder headerSb = new StringBuilder("|");
            StringBuilder separatorSb = new StringBuilder("|");
            
            for (int j = 0; j < maxCols; j++) {
                String val = "";
                if (headerRow != null) {
                    val = getCellValueAsString(headerRow.getCell(j, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                }
                headerSb.append(" ").append(val).append(" |");
                separatorSb.append(" --- |");
            }
            header = headerSb.append("\n").append(separatorSb).append("\n").toString();
        }

        StringBuilder currentChunk = new StringBuilder();
        // 如果有表头，初始化 chunk 为表头
        if (!header.isEmpty()) {
            currentChunk.append(header);
        }

        // 遍历数据行 (从 firstRowNum + 1 开始，因为第一行已作为表头)
        for (int i = firstRowNum + 1; i <= lastRowNum; i++) {
            StringBuilder rowSb = new StringBuilder();

            // 1. 检查该行是否有图片 (插入在行数据之前)
            if (imageDescriptions.containsKey(i)) {
                for (String desc : imageDescriptions.get(i)) {
                    rowSb.append("\n> **[图表/图片分析]** ").append(desc).append("\n\n");
                }
            }

            // 2. 构建数据行
            Row row = sheet.getRow(i);
            boolean hasContent = false;
            if (row != null && maxCols > 0) {
                rowSb.append("|");
                for (int j = 0; j < maxCols; j++) {
                    Cell cell = row.getCell(j, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String val = getCellValueAsString(cell);
                    if (!val.isBlank()) hasContent = true;
                    rowSb.append(" ").append(val).append(" |");
                }
                rowSb.append("\n");
            }

            // 如果全是空行且没有图片，跳过
            if (!hasContent && !imageDescriptions.containsKey(i)) {
                continue; 
            }

            String rowString = rowSb.toString();

            // 3. 检查容量
            if (currentChunk.length() + rowString.length() > maxChunkSize) {
                // 当前 Chunk 已满，保存
                // 只有当 chunk 包含除表头以外的内容时才保存
                if (currentChunk.length() > header.length()) {
                    segments.add(createSegment(currentChunk.toString(), sheetName, sourceName));
                }
                
                // 开启新 Chunk
                currentChunk = new StringBuilder();
                if (!header.isEmpty()) {
                    currentChunk.append(header); // 补全表头
                }
            }
            
            currentChunk.append(rowString);
        }
        
        // 处理最后剩余的 Chunk
        if (currentChunk.length() > header.length()) {
            segments.add(createSegment(currentChunk.toString(), sheetName, sourceName));
        }
        
        // 处理表格之后的图片（如果有）
        // 这里需要检查 imageDescriptions 中 index > lastRowNum 的部分
        boolean hasTrailingImages = false;
        StringBuilder trailingImagesSb = new StringBuilder();
        for (Map.Entry<Integer, List<String>> entry : imageDescriptions.entrySet()) {
            if (entry.getKey() > lastRowNum) {
                for (String desc : entry.getValue()) {
                    trailingImagesSb.append("\n> **[图表/图片分析]** ").append(desc).append("\n\n");
                    hasTrailingImages = true;
                }
            }
        }
        
        if (hasTrailingImages) {
             if (currentChunk.length() + trailingImagesSb.length() <= maxChunkSize && !currentChunk.isEmpty()) {
                 // 尝试追加到最后一个 chunk (如果还没保存的话... 实际上上面的逻辑已经保存了或还在 currentChunk 中)
                 // 如果 currentChunk 已经在上面被保存了（通过 length check），这里 currentChunk 又是新的 header。
                 // 这里的逻辑稍微有点复杂，为了简单，直接作为新 segment 或追加。
                 // 重新利用 currentChunk 逻辑：
             }
             // 简单处理：作为独立的 segment
             segments.add(createSegment(trailingImagesSb.toString(), sheetName, sourceName));
        }

        return segments;
    }

    private TextSegment createSegment(String text, String sheetName, String sourceName) {
        // 文本清洗
        text = TextCleaner.cleanExcelOutput(text);
        return TextSegment.from(
                text,
                Metadata.from("source", sourceName)
                        .put("type", "excel")
                        .put("sheet_name", sheetName));
    }
    
    /**
     * 提取 Sheet 中的图片并调用视觉模型分析
     */
    private Map<Integer, List<String>> extractImages(Sheet sheet) {
        Map<Integer, List<String>> descriptions = new HashMap<>();
        
        if (!visionService.isEnabled()) {
            return descriptions;
        }

        Drawing<?> drawing = sheet.getDrawingPatriarch();
        if (drawing == null) {
            return descriptions;
        }

        for (Shape shape : drawing) {
            if (shape instanceof Picture picture) {
                PictureData pictureData = picture.getPictureData();
                if (pictureData == null) continue;
                
                ClientAnchor anchor = picture.getClientAnchor();
                int row = (anchor != null) ? anchor.getRow1() : 0; // 图片所在起始行

                try {
                    byte[] data = pictureData.getData();
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                    if (image == null) continue; // 非图片数据

                    log.debug("正在分析工作表 '{}' 第 {} 行的图片", sheet.getSheetName(), row);
                    String description = visionService.analyzeImage(image, "请详细描述这张图片的内容。如果它是一个图表（如柱状图、折线图、饼图等），请提取其中的关键数据、趋势和图例信息。如果是表格截图，请尝试还原数据。");
                    
                    descriptions.computeIfAbsent(row, k -> new ArrayList<>()).add(description);
                    
                } catch (Exception e) {
                    log.error("处理工作表 '{}' 第 {} 行的图片失败: {}", 
                            sheet.getSheetName(), row, e.getMessage());
                }
            }
        }
        
        return descriptions;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        DataFormatter formatter = new DataFormatter();
        String value = formatter.formatCellValue(cell);
        
        // 清理换行符和竖线，防止破坏 Markdown 表格结构
        return value.replace("\n", " ").replace("\r", "").replace("|", "\\|");
    }

    @Override
    public boolean supports(String filename) {
        if (filename == null)
            return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".xls") || lower.endsWith(".xlsx");
    }

    @Override
    public int priority() {
        return 20;
    }
}
