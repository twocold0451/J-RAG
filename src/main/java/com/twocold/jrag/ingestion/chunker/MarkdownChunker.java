package com.twocold.jrag.ingestion.chunker;

import com.twocold.jrag.config.RagProperties;
import com.twocold.jrag.ingestion.utils.TextCleaner;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 文档切分器
 * 基于标题层级进行切分，保留文档结构信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarkdownChunker implements DocumentChunker {

    private final RagProperties ragProperties;

    // 匹配 Markdown 标题 (# 到 ######)
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);

    @Override
    public List<TextSegment> chunk(Path filePath) {
        log.debug("对 Markdown 文档使用 MarkdownChunker");

        Document document;
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            UniversalDetector detector = new UniversalDetector(null);
            detector.handleData(bytes, 0, Math.min(bytes.length, 4096));
            detector.dataEnd();
            String encoding = detector.getDetectedCharset();
            if (encoding == null)
                encoding = "UTF-8";

            String rawText = new String(bytes, Charset.forName(encoding));
            // 文本清洗
            rawText = TextCleaner.clean(rawText);
            document = Document.from(rawText);
        } catch (IOException e) {
            log.error("读取 Markdown 文件失败: {}", filePath, e);
            throw new RuntimeException("读取 Markdown 文件失败", e);
        }

        List<TextSegment> segments = new ArrayList<>();
        String[] lines = document.text().split("\\R"); // 匹配任何换行符
        
        // 递归切分器，用于处理超长的章节内容
        DocumentSplitter recursiveSplitter = DocumentSplitters.recursive(
                ragProperties.chunking().size(),
                ragProperties.chunking().overlap());

        List<String> currentPath = new ArrayList<>();
        StringBuilder currentSectionContent = new StringBuilder();
        
        for (String line : lines) {
            Matcher matcher = HEADING_PATTERN.matcher(line);
            if (matcher.matches()) {
                // 1. 如果当前缓冲区有内容，先保存为一个片段
                if (!currentSectionContent.isEmpty()) {
                    createSegmentsFromSection(segments, currentSectionContent.toString(), currentPath, filePath, recursiveSplitter);
                    currentSectionContent.setLength(0);
                }

                // 2. 更新标题路径
                int level = matcher.group(1).length(); // # 的数量
                String title = matcher.group(2).trim();

                // 调整路径层级：保留 level-1 个父标题，然后添加当前标题
                // 例如：当前是 [H1, H2]，遇到 H2 -> [H1, newH2]
                // 遇到 H3 -> [H1, H2, newH3]
                // 遇到 H1 -> [newH1]
                if (level > currentPath.size()) {
                    // 层级加深，直接添加 (可能跳级，如 H1 -> H3，也直接加)
                    currentPath.add(title);
                } else {
                    // 层级同级或变浅，保留前 level-1 个
                    while (currentPath.size() >= level) {
                        currentPath.removeLast();
                    }
                    currentPath.add(title);
                }
                
                // 将标题行也加入内容，保持上下文完整性
                currentSectionContent.append(line).append("\n");

            } else {
                // 普通行，追加到当前章节
                currentSectionContent.append(line).append("\n");
            }
        }

        // 处理最后一个章节
        if (!currentSectionContent.isEmpty()) {
            createSegmentsFromSection(segments, currentSectionContent.toString(), currentPath, filePath, recursiveSplitter);
        }

        log.info("MarkdownChunker 生成了 {} 个具有结构感知的片段", segments.size());
        return segments;
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
                                    .put("type", "markdown")
                                    .put("header_path", pathString);

        // 如果章节内容本身很短，直接作为一个 chunk
        if (contentStr.length() <= ragProperties.chunking().size()) {
            segments.add(TextSegment.from(contentStr, metadata));
        } else {
            // 如果章节内容过长，在内部进行递归切分
            // 注意：LangChain4j 的 split 会丢失自定义 metadata，所以我们需要手动重新包装
            List<TextSegment> subSegments = splitter.split(Document.from(contentStr));
            for (TextSegment sub : subSegments) {
                segments.add(TextSegment.from(sub.text(), metadata));
            }
        }
    }

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".md");
    }

    @Override
    public int priority() {
        return 10;
    }
}
