package com.twocold.jrag.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twocold.jrag.domain.Chunk;
import com.twocold.jrag.service.QueryDecompositionService;
import com.twocold.jrag.service.RetrievalService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Agent 工具箱
 * 提供给 DeepThinkingAgent 调用的具体能力（Function Calling）。
 * 注意：为了支持流式输出思考过程和并发安全，该类应针对每个请求实例化。
 */
@Slf4j
public class RagAgentTools {

    private final RetrievalService retrievalService;
    private final QueryDecompositionService decompositionService;
    private final FluxSink<ServerSentEvent<String>> sink;
    private final List<UUID> documentIds;
    private final ObjectMapper objectMapper;

    // 防止死循环的工具调用限制
    private int toolCallCount = 0;
    private static final int MAX_TOOL_CALLS = 5;

    public RagAgentTools(RetrievalService retrievalService,
                          QueryDecompositionService decompositionService,
                          FluxSink<ServerSentEvent<String>> sink,
                          List<UUID> documentIds,
                          ObjectMapper objectMapper) {
        this.retrievalService = retrievalService;
        this.decompositionService = decompositionService;
        this.sink = sink;
        this.documentIds = documentIds;
        this.objectMapper = objectMapper;
    }

    private void emitThought(String thought) {
        if (sink != null) {
            sink.next(ServerSentEvent.<String>builder()
                    .event("thought")
                    .data(thought)
                    .build());
        }
    }

    /**
     * 工具：搜索知识库
     * 根据关键词执行混合检索。
     * 
     * @param query 搜索关键词
     * @return 检索到的相关文档片段
     */
    @Tool("在知识库中搜索相关信息。当需要获取事实数据、文档内容或具体细节时使用此工具。")
    public String searchKnowledgeBase(String query) {
        // 检查工具调用次数限制，防止死循环
        if (toolCallCount >= MAX_TOOL_CALLS) {
            emitThought("已达到最大工具调用次数限制，停止搜索");
            return "工具调用次数已达上限，无法继续搜索。请基于现有信息回答。";
        }
        toolCallCount++;

        // 只记录工具调用和查询长度，不输出具体内容以保护用户隐私
        String logMsg = String.format("Agent 调用工具 [searchKnowledgeBase]: 查询长度 %d 字符", query != null ? query.length() : 0);
        log.info(logMsg);
        emitThought("正在搜索知识库: " + query); // 发送思考过程
        
        if (log.isDebugEnabled()) {
            log.debug("searchKnowledgeBase 查询详情（已脱敏）: '{}'", com.twocold.jrag.utils.LogMaskingUtils.maskQuery(query));
        }
        try {
            List<Chunk> results = retrievalService.hybridSearch(query, documentIds);

            if (results.isEmpty()) {
                emitThought("未找到相关信息");
                return "未在知识库中找到关于 '" + query + "' 的相关信息。尝试简化关键词或换一种说法。";
            }

            emitThought("找到 " + results.size() + " 条相关片段");
            return results.stream()
                    .map(chunk -> {
                        String sourceInfo = parseSourceMeta(chunk.getSourceMeta());
                        return String.format("[来源: %s]\n", sourceInfo);
                    })
                    .distinct()
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            log.error("工具调用失败", e);
            emitThought("搜索出错: " + e.getMessage());
            return "搜索过程中发生错误: " + e.getMessage();
        }
    }

    /**
     * 解析来源元数据，提取文件名称和页码信息
     * @param sourceMetaJson JSON格式的来源元数据
     * @return 格式化的来源信息字符串
     */
    private String parseSourceMeta(String sourceMetaJson) {
        if (sourceMetaJson == null || sourceMetaJson.trim().isEmpty()) {
            return "未知来源";
        }
        try {
            Map<String, Object> meta = objectMapper.readValue(sourceMetaJson, Map.class);
            String fileName = (String) meta.get("source");
            Object pageObj = meta.get("page");

            // 清理文件名，去掉开头的 upload- 和数字部分
            if (fileName != null) {
                fileName = cleanFileName(fileName);
            }

            if (fileName != null && pageObj != null) {
                return "文件: " + fileName + ", 页码: " + pageObj.toString();
            } else if (fileName != null) {
                return "文件: " + fileName;
            }
            return "未知来源";
        } catch (Exception e) {
            log.warn("解析来源元数据失败: {}", e.getMessage());
            return sourceMetaJson; // 解析失败时返回原始JSON作为后备
        }
    }

    /**
     * 清理文件名，去掉开头的临时文件前缀
     * @param fileName 原始文件名
     * @return 清理后的文件名
     */
    private String cleanFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        // 匹配 upload- 后面跟随机字符和连字符，然后是实际文件名
        // 例如: upload-123456789-filename.pdf -> filename.pdf
        String pattern = "^upload-.*-";
        return fileName.replaceFirst(pattern, "");
    }

    /**
     * 工具：拆解复杂查询
     * 利用 LLM 将长难句拆解为简单句。
     * 
     * @param query 原始复杂问题
     * @return 建议的子查询列表
     */
    @Tool("将复杂问题拆解为多个简单的子查询。当用户问题包含多个部分、对比分析或逻辑复杂时使用。返回拆解后的子问题列表。")
    public String decomposeQuery(String query) {
        // 检查工具调用次数限制，防止死循环
        if (toolCallCount >= MAX_TOOL_CALLS) {
            emitThought("已达到最大工具调用次数限制，停止拆解");
            return "工具调用次数已达上限，无法继续拆解。请基于现有信息回答。";
        }
        toolCallCount++;

        // 只记录工具调用和查询长度，不输出具体内容以保护用户隐私
        String logMsg = String.format("Agent 调用工具 [decomposeQuery]: 查询长度 %d 字符", query != null ? query.length() : 0);
        log.info(logMsg);
        emitThought("正在拆解复杂问题: " + query); // 发送思考过程

            if (log.isDebugEnabled()) {
            log.debug("decomposeQuery 查询详情（已脱敏）: '{}'", com.twocold.jrag.utils.LogMaskingUtils.maskQuery(query));
        }
        try {
            List<String> subQueries = decompositionService.decompose(query);
            emitThought("拆解为 " + subQueries.size() + " 个子问题");
            return "建议将问题拆解为以下子查询进行搜索:\n" + String.join("\n", subQueries);
        } catch (Exception e) {
            log.error("工具调用失败", e);
            emitThought("拆解问题出错: " + e.getMessage());
            return "拆解问题失败: " + e.getMessage();
        }
    }
}