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
import org.apache.poi.xwpf.usermodel.*;
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
 * Word 文档切分器
 * 支持 .doc 和 .docx 格式
 * 针对 .docx 格式进行深度结构化解析（标题、表格、图片）
 * .doc 格式使用回退策略（纯文本提取）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WordChunker implements DocumentChunker {

    private final RagProperties ragProperties;
    private final VisionService visionService;

    @Override
    public List<TextSegment> chunk(Path filePath) {
        log.debug("Using WordChunker for document: {}", filePath.getFileName());

        String filename = filePath.getFileName().toString().toLowerCase();
        
        // 针对 .docx 使用高级结构化解析
        if (filename.endsWith(".docx")) {
            try {
                return processDocx(filePath);
            } catch (Exception e) {
                log.error("Failed to process .docx structually, falling back to plain text: {}", e.getMessage());
                // Fallback to plain text if structural parsing fails
            }
        }

        // 针对 .doc 或 .docx 解析失败的情况，使用标准 POI 解析器 (纯文本)
        return processLegacyOrFallback(filePath);
    }

    private List<TextSegment> processDocx(Path filePath) throws IOException {
        List<TextSegment> segments = new ArrayList<>();
        
        try (InputStream is = new FileInputStream(filePath.toFile());
             XWPFDocument doc = new XWPFDocument(is)) {

            List<String> currentHeaderPath = new ArrayList<>();
            StringBuilder currentSectionContent = new StringBuilder();
            
            // 递归切分器，用于处理超长的章节内容
            DocumentSplitter recursiveSplitter = DocumentSplitters.recursive(
                    ragProperties.chunking().size(),
                    ragProperties.chunking().overlap());

            // 遍历文档主体元素 (段落和表格)
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph) {
                    processParagraph((XWPFParagraph) element, currentHeaderPath, currentSectionContent, segments, filePath, recursiveSplitter);
                } else if (element instanceof XWPFTable) {
                    String markdownTable = processTable((XWPFTable) element);
                    if (!markdownTable.isEmpty()) {
                        currentSectionContent.append(markdownTable).append("\n\n");
                    }
                }
            }

            // 处理最后一个章节
            if (!currentSectionContent.isEmpty()) {
                createSegmentsFromSection(segments, currentSectionContent.toString(), currentHeaderPath, filePath, recursiveSplitter);
            }
        }
        return segments;
    }

    private void processParagraph(XWPFParagraph paragraph, 
                                  List<String> currentHeaderPath, 
                                  StringBuilder currentSectionContent,
                                  List<TextSegment> segments,
                                  Path filePath,
                                  DocumentSplitter splitter) {
        
        String style = paragraph.getStyle();
        String text = paragraph.getText();
        
        // 提取图片
        List<String> imageDescriptions = extractImagesFromParagraph(paragraph);
        if (!imageDescriptions.isEmpty()) {
            for (String desc : imageDescriptions) {
                currentSectionContent.append("\n> **[图片分析]** ").append(desc).append("\n\n");
            }
        }

        // 检查是否为标题 (Heading 1 ~ Heading 6)
        // 注意：Word 的 Style ID 可能是 "Heading1", "1", "Heading 1" 等，取决于本地化
        if (style != null && style.toLowerCase().startsWith("heading")) {
            // 发现新标题，归档之前的章节
            if (!currentSectionContent.isEmpty()) {
                createSegmentsFromSection(segments, currentSectionContent.toString(), currentHeaderPath, filePath, splitter);
                currentSectionContent.setLength(0);
            }

            // 更新标题路径
            // 尝试解析层级，例如 "Heading 1" -> level 1
            int level = 1; // 默认为 1
            try {
                String levelStr = style.replaceAll("[^0-9]", "");
                if (!levelStr.isEmpty()) {
                    level = Integer.parseInt(levelStr);
                }
            } catch (NumberFormatException ignored) {}

            updateHeaderPath(currentHeaderPath, text, level);
            
            // 将标题文本也加入新章节
            currentSectionContent.append("# ".repeat(level)).append(" ").append(text).append("\n\n");

        } else {
            // 普通段落
            if (!text.isBlank()) {
                currentSectionContent.append(text).append("\n");
            }
        }
    }

    private List<String> extractImagesFromParagraph(XWPFParagraph paragraph) {
        List<String> descriptions = new ArrayList<>();
        if (!visionService.isEnabled()) return descriptions;

        for (XWPFRun run : paragraph.getRuns()) {
            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                XWPFPictureData pictureData = picture.getPictureData();
                try {
                    byte[] data = pictureData.getData();
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                    if (image != null) {
                        String desc = visionService.analyzeImage(image, "请描述这张图片的内容。如果是图表或流程图，请详细解释。");
                        descriptions.add(desc);
                    }
                } catch (Exception e) {
                    log.warn("Failed to analyze image in Word doc: {}", e.getMessage());
                }
            }
        }
        return descriptions;
    }

    private String processTable(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) return "";

        // 计算最大列数
        int maxCols = 0;
        for (XWPFTableRow row : rows) {
            maxCols = Math.max(maxCols, row.getTableCells().size());
        }

        // 生成 Markdown 表格
        for (int i = 0; i < rows.size(); i++) {
            XWPFTableRow row = rows.get(i);
            List<XWPFTableCell> cells = row.getTableCells();
            
            sb.append("|");
            for (int j = 0; j < maxCols; j++) {
                String cellText = (j < cells.size()) ? cells.get(j).getText().replace("\n", " ").replace("|", "\\|") : "";
                sb.append(" ").append(cellText).append(" |");
            }
            sb.append("\n");

            // 分隔线
            if (i == 0) {
                sb.append("|");
                sb.append(" ---".repeat(maxCols));
                sb.append("|\n");
            }
        }
        return sb.toString();
    }

    private void updateHeaderPath(List<String> currentPath, String title, int level) {
        if (level > currentPath.size()) {
            currentPath.add(title);
        } else {
            while (currentPath.size() >= level) {
                currentPath.remove(currentPath.size() - 1);
            }
            currentPath.add(title);
        }
    }

    private void createSegmentsFromSection(List<TextSegment> segments, 
                                           String content, 
                                           List<String> headerPath, 
                                           Path filePath,
                                           DocumentSplitter splitter) {
        String contentStr = content.trim();
        if (contentStr.isEmpty()) return;

        String pathString = String.join(" > ", headerPath);
        Metadata metadata = Metadata.from("source", filePath.getFileName().toString())
                                    .put("type", "word")
                                    .put("header_path", pathString);

        if (contentStr.length() <= ragProperties.chunking().size()) {
            segments.add(TextSegment.from(contentStr, metadata));
        } else {
            List<TextSegment> subSegments = splitter.split(Document.from(contentStr));
            for (TextSegment sub : subSegments) {
                segments.add(TextSegment.from(sub.text(), metadata));
            }
        }
    }

    private List<TextSegment> processLegacyOrFallback(Path filePath) {
        Document document;
        try (InputStream is = new FileInputStream(filePath.toFile())) {
            document = new ApachePoiDocumentParser().parse(is);
        } catch (IOException e) {
            log.error("Failed to parse Word file: {}", filePath, e);
            throw new RuntimeException("Failed to parse Word file", e);
        }

        DocumentSplitter splitter = DocumentSplitters.recursive(
                ragProperties.chunking().size(),
                ragProperties.chunking().overlap());

        List<TextSegment> segments = splitter.split(document);

        return segments.stream()
                .map(seg -> TextSegment.from(
                        seg.text(),
                        Metadata.from("source", filePath.getFileName().toString())
                                .put("type", "word")))
                .toList();
    }

    @Override
    public boolean supports(String filename) {
        if (filename == null)
            return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".doc") || lower.endsWith(".docx");
    }

    @Override
    public int priority() {
        return 20;
    }
}
