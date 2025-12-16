package com.example.qarag.ingestion.chunker;

import com.example.qarag.config.RagProperties;
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
        log.debug("Using RecursiveChunker with size={}, overlap={}",
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

            document = Document.from(new String(bytes, Charset.forName(encoding)));
        } catch (IOException e) {
            log.error("Failed to read file: {}", filePath, e);
            throw new RuntimeException("Failed to read file", e);
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
