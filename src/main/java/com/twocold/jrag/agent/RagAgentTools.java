package com.twocold.jrag.agent;

import com.twocold.jrag.domain.Chunk;
import com.twocold.jrag.service.QueryDecompositionService;
import com.twocold.jrag.service.RetrievalService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.FluxSink;

import java.util.List;
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

    public RagAgentTools(RetrievalService retrievalService, 
                         QueryDecompositionService decompositionService,
                         FluxSink<ServerSentEvent<String>> sink,
                         List<UUID> documentIds) {
        this.retrievalService = retrievalService;
        this.decompositionService = decompositionService;
        this.sink = sink;
        this.documentIds = documentIds;
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
                    .map(chunk -> String.format("[来源: %s]\n内容: %s", chunk.getSourceMeta(), chunk.getContent()))
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            log.error("工具调用失败", e);
            emitThought("搜索出错: " + e.getMessage());
            return "搜索过程中发生错误: " + e.getMessage();
        }
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