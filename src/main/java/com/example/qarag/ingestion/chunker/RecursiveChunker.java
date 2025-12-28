package com.example.qarag.ingestion.chunker;

import com.example.qarag.config.RagProperties;
import com.example.qarag.ingestion.utils.TextCleaner;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 默认的递归切分策略
 * 使用 LangChain4J 内置的递归切分器
 * 作为 fallback，支持所有文件类型
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class RecursiveChunker implements DocumentChunker {

    private final RagProperties ragProperties;

    @Override
    public List<TextSegment> chunk(Path filePath) {
        log.debug("使用 RecursiveChunker，大小={}，重叠={}",
                ragProperties.chunking().size(),
                ragProperties.chunking().overlap());

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
            log.error("读取文件失败: {}", filePath, e);
            throw new RuntimeException("读取文件失败", e);
        }

        DocumentSplitter splitter = DocumentSplitters.recursive(
                ragProperties.chunking().size(),
                ragProperties.chunking().overlap());
        return splitter.split(document);
    }

    @Override
    public boolean supports(String filename) {
        // 作为 fallback，支持所有格式
        return true;
    }

    @Override
    public int priority() {
        // 最低优先级，当没有其他 Chunker 匹配时使用
        return Integer.MAX_VALUE;
    }
}
