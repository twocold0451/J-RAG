package com.twocold.jrag.agent;

import com.twocold.jrag.domain.Chunk;
import com.twocold.jrag.service.RetrievalService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Subagent 执行器
 * 为每个子查询创建独立的 Agent 实例，每个 Agent 有自己的 LLM 上下文和工具集
 */
@Slf4j
public class SubAgentExecutor {

    private final ChatModel chatModel;
    private final RetrievalService retrievalService;
    private final List<UUID> documentIds;
    private final Executor executor;

    public SubAgentExecutor(ChatModel chatModel,
                            RetrievalService retrievalService,
                            List<UUID> documentIds,
                            Executor executor) {
        this.chatModel = chatModel;
        this.retrievalService = retrievalService;
        this.documentIds = documentIds;
        this.executor = executor;
    }

    /**
     * 并行执行多个子查询搜索
     * 每个子查询由独立的 SubAgent 处理
     *
     * @param subQueries 子查询列表
     * @return 每个子查询的搜索结果
     */
    public CompletableFuture<List<SubAgentResult>> executeAll(List<String> subQueries) {
        log.info("启动 {} 个 SubAgent 并行执行", subQueries.size());

        // 为每个子查询创建一个独立的 SubAgent 任务
        List<CompletableFuture<SubAgentResult>> futures = subQueries.stream()
                .map(query -> CompletableFuture
                        .supplyAsync(() -> createAndExecuteSubAgent(query, subQueries.indexOf(query)), executor)
                )
                .toList();

        // 等待所有 SubAgent 完成
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /**
     * 创建并执行单个 SubAgent
     * 每个 SubAgent 是独立的 AiServices 实例，有自己的 LLM 上下文
     *
     * @param query 子查询
     * @param index 子查询索引
     * @return 搜索结果
     */
    private SubAgentResult createAndExecuteSubAgent(String query, int index) {
        log.info("[SubAgent #{}] 创建实例，任务：{}", index + 1, query);

        try {
            // 创建独立的 SubAgent 实例
            // 关键点：每个 SubAgent 有自己的 AiServices 实例和 LLM 上下文
            SearchSubAgent subAgent = AiServices.builder(SearchSubAgent.class)
                    .chatModel(chatModel)
                    .tools(new SubAgentSearchTool(retrievalService, documentIds, index))
                    .build();

            // 执行搜索 - SubAgent 可以自主决定是否调用工具、调用几次
            String result = subAgent.search(query);

            log.info("[SubAgent #{}] 执行完成", index + 1);
            return new SubAgentResult(query, result, null, index, "q" + (index + 1));

        } catch (Exception e) {
            log.error("[SubAgent #{}] 执行失败：{}", index + 1, e.getMessage(), e);
            return new SubAgentResult(query, null, e, index, "q" + (index + 1));
        }
    }

    /**
     * SubAgent 执行结果
     */
    public record SubAgentResult(
            String query,           // 子查询
            String result,          // 搜索结果
            Exception error,        // 错误信息
            int agentIndex,         // SubAgent 索引
            String queryId          // 子查询ID
    ) {}

    /**
     * 按依赖关系分层执行子查询
     * 使用拓扑排序确定执行顺序，同层级并行执行
     *
     * @param decompositionResult 带依赖关系的分解结果
     * @return 每个子查询的搜索结果
     */
    public List<SubAgentResult> executeWithDependencies(DecompositionResult decompositionResult) {
        if (decompositionResult == null || decompositionResult.isEmpty()) {
            return Collections.emptyList();
        }

        List<SubQuery> subQueries = decompositionResult.getSubQueries();
        Map<String, List<String>> dependencyGraph = decompositionResult.getDependencyGraph();

        log.info("启动依赖感知的 SubAgent 执行，共 {} 个子查询", subQueries.size());

        // 1. 拓扑排序，确定执行层级
        List<List<String>> executionLayers = topologicalSort(dependencyGraph);
        log.debug("执行层级划分: {}", executionLayers);

        // 2. 按层级执行
        Map<String, SubAgentResult> completedResults = new ConcurrentHashMap<>();
        Map<String, SubQuery> queryMap = subQueries.stream()
                .collect(Collectors.toMap(SubQuery::getId, sq -> sq));

        for (int layerIndex = 0; layerIndex < executionLayers.size(); layerIndex++) {
            List<String> layer = executionLayers.get(layerIndex);
            final int currentLayerIndex = layerIndex; // 用于lambda
            log.info("执行第 {} 层，包含 {} 个子查询: {}", layerIndex + 1, layer.size(), layer);

            // 同层级并行执行
            List<CompletableFuture<Void>> futures = layer.stream()
                    .map(queryId -> CompletableFuture.runAsync(() -> {
                        SubQuery sq = queryMap.get(queryId);
                        if (sq == null) {
                            log.warn("未找到子查询: {}", queryId);
                            return;
                        }

                        // 构建依赖上下文
                        String dependencyContext = buildDependencyContext(sq, completedResults);

                        // 执行SubAgent
                        SubAgentResult result = createAndExecuteSubAgentWithContext(
                                sq, dependencyContext, currentLayerIndex);
                        completedResults.put(queryId, result);
                    }, executor))
                    .toList();

            // 等待当前层级全部完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("第 {} 层执行完成", layerIndex + 1);
        }

        // 3. 按原始顺序返回结果
        return subQueries.stream()
                .map(sq -> completedResults.get(sq.getId()))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 构建依赖上下文
     * 将前置子查询的结果汇总，作为当前子查询的上下文
     */
    private String buildDependencyContext(SubQuery sq, Map<String, SubAgentResult> completedResults) {
        if (sq.getDependsOn() == null || sq.getDependsOn().isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("【前置查询结果上下文】\n");
        context.append("以下是你依赖的前置子查询的结果，请在回答当前问题时充分利用这些信息：\n\n");

        for (String depId : sq.getDependsOn()) {
            SubAgentResult depResult = completedResults.get(depId);
            if (depResult != null) {
                context.append(String.format("--- 前置查询 [%s] ---\n", depId));
                context.append(String.format("问题：%s\n", depResult.query()));
                if (depResult.error() != null) {
                    context.append(String.format("结果：查询失败 - %s\n", depResult.error().getMessage()));
                } else if (depResult.result() != null) {
                    // 截断过长的结果，避免上下文爆炸
                    String result = depResult.result();
                    if (result.length() > 2000) {
                        result = result.substring(0, 2000) + "... (已截断)";
                    }
                    context.append(String.format("结果：%s\n", result));
                } else {
                    context.append("结果：无\n");
                }
                context.append("\n");
            }
        }

        context.append("【当前任务】\n");
        context.append(String.format("请基于上述前置结果，回答：%s\n", sq.getQuery()));

        return context.toString();
    }

    /**
     * 创建并执行单个 SubAgent（带依赖上下文）
     */
    private SubAgentResult createAndExecuteSubAgentWithContext(SubQuery subQuery,
                                                               String dependencyContext,
                                                               int layerIndex) {
        String query = subQuery.getQuery();
        String queryId = subQuery.getId();
        log.info("[SubAgent {}] 创建实例（第{}层），任务：{}", queryId, layerIndex + 1, query);

        try {
            // 构建完整的查询（包含依赖上下文）
            String fullQuery = dependencyContext.isEmpty() ? query :
                    dependencyContext + "\n\n【需要回答的问题】\n" + query;

            // 创建独立的 SubAgent 实例
            SubAgentSearchTool searchTool = new SubAgentSearchTool(
                    retrievalService, documentIds, Integer.parseInt(queryId.replace("q", "")) - 1);

            SearchSubAgent subAgent = AiServices.builder(SearchSubAgent.class)
                    .chatModel(chatModel)
                    .tools(searchTool)
                    .build();

            // 执行搜索
            String result = subAgent.search(fullQuery);

            log.info("[SubAgent {}] 执行完成", queryId);
            return new SubAgentResult(query, result, null, Integer.parseInt(queryId.replace("q", "")) - 1, queryId);

        } catch (Exception e) {
            log.error("[SubAgent {}] 执行失败：{}", queryId, e.getMessage(), e);
            return new SubAgentResult(query, null, e, Integer.parseInt(queryId.replace("q", "")) - 1, queryId);
        }
    }

    /**
     * 拓扑排序
     * 将依赖图分层，每层内的节点可以并行执行
     *
     * @param graph 依赖图 (key: 节点ID, value: 依赖的节点ID列表)
     * @return 执行层级，每层是一个节点ID列表
     */
    private List<List<String>> topologicalSort(Map<String, List<String>> graph) {
        List<List<String>> layers = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> remaining = new HashSet<>(graph.keySet());

        // 检查所有依赖的节点是否都在图中
        for (Map.Entry<String, List<String>> entry : graph.entrySet()) {
            for (String dep : entry.getValue()) {
                if (!graph.containsKey(dep)) {
                    log.warn("子查询 {} 依赖的节点 {} 不在图中，将被忽略", entry.getKey(), dep);
                }
            }
        }

        while (!remaining.isEmpty()) {
            List<String> currentLayer = new ArrayList<>();

            for (String node : remaining) {
                List<String> dependencies = graph.getOrDefault(node, Collections.emptyList());
                // 节点的所有依赖都已被访问，则该节点可以加入当前层
                boolean allDepsVisited = dependencies.stream()
                        .filter(graph::containsKey)  // 只检查在图中的依赖
                        .allMatch(visited::contains);

                if (allDepsVisited) {
                    currentLayer.add(node);
                }
            }

            if (currentLayer.isEmpty()) {
                // 检测到循环依赖或无法处理的依赖
                log.error("检测到可能的循环依赖或无效依赖，剩余节点: {}", remaining);
                // 将剩余节点作为一个层处理，避免死循环
                currentLayer.addAll(remaining);
            }

            layers.add(currentLayer);
            visited.addAll(currentLayer);
            currentLayer.forEach(remaining::remove);

            log.debug("拓扑排序第 {} 层: {}", layers.size(), currentLayer);
        }

        return layers;
    }
}
