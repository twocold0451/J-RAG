package com.twocold.jrag.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twocold.jrag.domain.Chunk;
import com.twocold.jrag.service.QueryDecompositionService;
import com.twocold.jrag.service.RetrievalService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
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
    private final ChatModel chatModel;
    private final Executor executor;

    // 防止死循环的工具调用限制
    private int toolCallCount = 0;
    private static final int MAX_TOOL_CALLS = 5;

    // 重复调用检测 - 只记录上一次调用
    private ToolCallRecord lastCallRecord = null;
    // Subagent 执行器
    private SubAgentExecutor subAgentExecutor;

    private record ToolCallRecord(String methodName, String paramsHash, boolean hasValidObservation) {}

    public RagAgentTools(RetrievalService retrievalService,
                          QueryDecompositionService decompositionService,
                          FluxSink<ServerSentEvent<String>> sink,
                          List<UUID> documentIds,
                          ObjectMapper objectMapper,
                          ChatModel chatModel,
                          Executor executor) {
        this.retrievalService = retrievalService;
        this.decompositionService = decompositionService;
        this.sink = sink;
        this.documentIds = documentIds;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
        this.executor = executor;
        this.subAgentExecutor = new SubAgentExecutor(chatModel, retrievalService, documentIds, executor);
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
     * 检查是否为连续重复调用
     * @param methodName 工具方法名
     * @param params 方法参数
     * @return true 如果检测到连续重复调用
     */
    private boolean isConsecutiveDuplicate(String methodName, String params) {
        String currentHash = Objects.toString(params, "");

        boolean isDuplicate = lastCallRecord != null
                           && methodName.equals(lastCallRecord.methodName())
                           && currentHash.equals(lastCallRecord.paramsHash())
                           && !lastCallRecord.hasValidObservation();

        // 记录本次调用（先假设无效，等执行完后更新）
        lastCallRecord = new ToolCallRecord(methodName, currentHash, false);

        return isDuplicate;
    }

    /**
     * 标记最近一次调用获得了有效结果
     */
    private void markLastCallAsValid() {
        if (lastCallRecord != null) {
            lastCallRecord = new ToolCallRecord(
                lastCallRecord.methodName(),
                lastCallRecord.paramsHash(),
                true
            );
        }
    }

    /**
     * 生成中断警告消息
     */
    private String generateInterruptionMessage(String methodName) {
        return String.format(
            "【系统警告】检测到你连续两次调用相同工具 '%s' 且未得到有效结果。" +
            "根据系统指令，请立即放弃当前工具调用，并仅基于已收集到的信息进行最终总结。",
            methodName
        );
    }

    /**
     * 格式化搜索结果
     */
    private String formatSearchResults(List<Chunk> results) {
        return results.stream()
                .map(chunk -> {
                    String sourceInfo = parseSourceMeta(chunk.getSourceMeta());
                    return String.format("[来源：%s]\n", sourceInfo);
                })
                .distinct()
                .collect(Collectors.joining("\n---\n"));
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
        // 检查工具调用次数限制
        if (toolCallCount >= MAX_TOOL_CALLS) {
            emitThought("已达到最大工具调用次数限制，停止搜索");
            return "工具调用次数已达上限，无法继续搜索。请基于现有信息回答。";
        }

        // 检查连续重复调用
        if (isConsecutiveDuplicate("searchKnowledgeBase", query)) {
            emitThought("⚠️ 检测到重复搜索，触发系统中断");
            return generateInterruptionMessage("searchKnowledgeBase");
        }

        toolCallCount++;

        String logMsg = String.format("Agent 调用工具 [searchKnowledgeBase]: 查询长度 %d 字符", query != null ? query.length() : 0);
        log.info(logMsg);
        emitThought("正在搜索知识库：" + query);

        if (log.isDebugEnabled()) {
            log.debug("searchKnowledgeBase 查询详情（已脱敏）：'{}'", com.twocold.jrag.utils.LogMaskingUtils.maskQuery(query));
        }

        try {
            List<Chunk> results = retrievalService.hybridSearch(query, documentIds);

            if (results.isEmpty()) {
                emitThought("未找到相关信息");
                return "未在知识库中找到关于 '" + query + "' 的相关信息。尝试简化关键词或换一种说法。";
            }

            // 标记本次调用获得了有效结果
            markLastCallAsValid();
            emitThought("找到 " + results.size() + " 条相关片段");
            return formatSearchResults(results);
        } catch (Exception e) {
            log.error("工具调用失败", e);
            emitThought("搜索出错：" + e.getMessage());
            return "搜索过程中发生错误：" + e.getMessage();
        }
    }

    /**
     * 解析来源元数据，提取文件名称和页码信息
     * @param sourceMetaJson JSON 格式的来源元数据
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
                return "文件：" + fileName + ", 页码：" + pageObj.toString();
            } else if (fileName != null) {
                return "文件：" + fileName;
            }
            return "未知来源";
        } catch (Exception e) {
            log.warn("解析来源元数据失败：{}", e.getMessage());
            return sourceMetaJson;
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
        String pattern = "^upload-.*-";
        return fileName.replaceFirst(pattern, "");
    }

    /**
     * 工具：拆解复杂查询并并行执行搜索
     * 利用 LLM 将长难句拆解为简单句，分析子问题间的依赖关系，然后按依赖顺序分层执行搜索。
     *
     * @param query 原始复杂问题
     * @return 汇总的搜索结果
     */
    @Tool("将复杂问题拆解为多个子查询并执行搜索。当问题包含多个部分、对比分析或需要同时获取多个方面的信息时使用。支持子问题间的依赖关系。")
    public String decomposeQuery(String query) {
        // 检查工具调用次数限制
        if (toolCallCount >= MAX_TOOL_CALLS) {
            emitThought("已达到最大工具调用次数限制，停止拆解");
            return "工具调用次数已达上限，无法继续拆解。请基于现有信息回答。";
        }

        // 检查连续重复调用
        if (isConsecutiveDuplicate("decomposeQuery", query)) {
            emitThought("⚠️ 检测到重复拆解，触发系统中断");
            return generateInterruptionMessage("decomposeQuery");
        }

        toolCallCount++;

        emitThought("正在拆解复杂问题并分析依赖关系：" + query);

        try {
            // 1. 使用依赖感知的拆解方法
            DecompositionResult decompositionResult = decompositionService.decomposeWithDependencies(query);
            List<SubQuery> subQueries = decompositionResult.getSubQueries();

            // 分析依赖关系
            long independentCount = subQueries.stream().filter(SubQuery::isIndependent).count();
            long dependentCount = subQueries.stream().filter(SubQuery::isDependent).count();

            emitThought(String.format("拆解为 %d 个子问题（%d个独立，%d个有依赖），按依赖关系分层执行...",
                    subQueries.size(), independentCount, dependentCount));

            // 2. 使用依赖感知的执行器
            List<SubAgentExecutor.SubAgentResult> results = subAgentExecutor
                    .executeWithDependencies(decompositionResult);

            // 3. 汇总结果
            StringBuilder summary = new StringBuilder();
            summary.append("已完成 ").append(results.size()).append(" 个子查询的搜索:\n\n");

            // 显示执行顺序和依赖关系
            if (dependentCount > 0) {
                summary.append("【执行说明】\n");
                for (int i = 0; i < subQueries.size(); i++) {
                    SubQuery sq = subQueries.get(i);
                    summary.append(String.format("  %d. [%s] %s (%s",
                            i + 1, sq.getId(), sq.getQuery(),
                            sq.isIndependent() ? "独立执行" : "依赖: " + sq.getDependsOn()));
                    if (sq.getReason() != null && !sq.getReason().isEmpty()) {
                        summary.append(", ").append(sq.getReason());
                    }
                    summary.append(")\n");
                }
                summary.append("\n");
            }

            // 详细结果
            summary.append("【搜索结果详情】\n");
            for (SubAgentExecutor.SubAgentResult result : results) {
                summary.append("▸ [").append(result.queryId() != null ? result.queryId() : "?").append("] ")
                       .append(result.query()).append(":\n");
                if (result.error() != null) {
                    summary.append("  ❌ 搜索失败：").append(result.error().getMessage()).append("\n");
                } else {
                    summary.append("  ").append(result.result() != null ? result.result() : "无结果").append("\n");
                }
                summary.append("\n");
            }

            // 4. 标记有效结果
            markLastCallAsValid();
            emitThought("子 Agent 全部完成（包含依赖关系处理）");

            return summary.toString();

        } catch (Exception e) {
            log.error("工具调用失败", e);
            emitThought("拆解问题出错：" + e.getMessage());
            return "拆解问题失败：" + e.getMessage();
        }
    }
}
