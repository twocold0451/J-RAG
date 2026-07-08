package com.twocold.jrag.agent;

import com.twocold.jrag.domain.Chunk;
import com.twocold.jrag.service.RetrievalService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SubAgent 专用工具
 * 每个 SubAgent 实例有独立的工具实例，可以追踪自己的执行状态
 */
@Slf4j
public class SubAgentSearchTool {

    private final RetrievalService retrievalService;
    private final List<UUID> documentIds;
    private final int subAgentIndex;  // SubAgent 索引，用于日志追踪
    private int toolCallCount = 0;    // 当前 SubAgent 的工具调用次数

    public SubAgentSearchTool(RetrievalService retrievalService,
                              List<UUID> documentIds,
                              int subAgentIndex) {
        this.retrievalService = retrievalService;
        this.documentIds = documentIds;
        this.subAgentIndex = subAgentIndex;
    }

    /**
     * 搜索知识库
     * SubAgent 可以自主决定：
     * 1. 是否调用搜索工具
     * 2. 使用什么关键词搜索
     * 3. 是否需要根据结果调整搜索策略
     */
    @Tool("在知识库中搜索相关信息。根据用户问题返回相关的文档片段。")
    public String searchKnowledgeBase(String query) {
        toolCallCount++;
        log.info("[SubAgent #{}] 工具调用 [{}]: searchKnowledgeBase(\"{}\")",
                subAgentIndex + 1, toolCallCount, maskQuery(query));

        try {
            List<Chunk> results = retrievalService.hybridSearch(query, documentIds);

            if (results.isEmpty()) {
                log.warn("[SubAgent #{}] 未找到结果：{}", subAgentIndex + 1, query);
                return "未找到相关信息，请尝试：\n" +
                       "1. 简化关键词\n" +
                       "2. 使用同义词\n" +
                       "3. 缩小搜索范围";
            }

            // 返回详细结果，供 SubAgent 分析
            String content = results.stream()
                    .limit(5)  // 限制返回数量
                    .map(chunk -> {
                        String source = parseSource(chunk.getSourceMeta());
                        String preview = chunk.getContent();
                        if (preview.length() > 200) {
                            preview = preview.substring(0, 200) + "...";
                        }
                        return String.format("【来源：%s】\n%s", source, preview);
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));

            log.info("[SubAgent #{}] 找到 {} 条结果", subAgentIndex + 1, results.size());
            return "找到 " + results.size() + " 条相关信息：\n\n" + content;

        } catch (Exception e) {
            log.error("[SubAgent #{}] 搜索失败", subAgentIndex + 1, e);
            return "搜索出错：" + e.getMessage();
        }
    }

    /**
     * 解析来源元数据
     */
    private String parseSource(String sourceMeta) {
        if (sourceMeta == null || sourceMeta.trim().isEmpty()) {
            return "未知来源";
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> meta = mapper.readValue(sourceMeta, java.util.Map.class);
            String fileName = (String) meta.get("source");
            Object page = meta.get("page");

            if (fileName != null) {
                fileName = fileName.replaceFirst("^upload-.*-", "");
                return page != null ? fileName + " (第" + page + "页)" : fileName;
            }
            return "未知来源";
        } catch (Exception e) {
            return sourceMeta;
        }
    }

    /**
     * 脱敏查询内容（用于日志）
     */
    private String maskQuery(String query) {
        if (query == null || query.length() <= 10) {
            return "***";
        }
        return query.substring(0, 3) + "..." + query.substring(query.length() - 3);
    }
}
