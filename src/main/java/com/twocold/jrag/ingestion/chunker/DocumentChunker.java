package com.twocold.jrag.ingestion.chunker;

import dev.langchain4j.data.segment.TextSegment;

import java.nio.file.Path;
import java.util.List;

/**
 * 文档切分策略接口
 * 不同的文档格式可以实现不同的切分策略
 */
public interface DocumentChunker {

    /**
     * 将文档切分为文本片段
     * 
     * @param filePath 原始文件路径（切分器需要直接读取文件）
     * @return 切分后的文本片段列表
     */
    List<TextSegment> chunk(Path filePath);

    /**
     * 判断是否支持该文件类型
     *
     * @param filename 文件名（包含扩展名）
     * @return 如果支持返回 true
     */
    boolean supports(String filename);

    /**
     * 获取优先级，数值越小优先级越高
     * 当多个 Chunker 都支持同一文件类型时，使用优先级最高的
     *
     * @return 优先级数值
     */
    default int priority() {
        return 100;
    }
}
