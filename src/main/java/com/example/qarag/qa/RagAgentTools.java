package com.example.qarag.qa;

import com.example.qarag.domain.Chunk;
import com.example.qarag.service.QueryDecompositionService;
import com.example.qarag.service.RetrievalService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagAgentTools {

    private final RetrievalService retrievalService;
    private final QueryDecompositionService decompositionService;

    @Tool("在知识库中搜索相关信息。当需要获取事实数据、文档内容或具体细节时使用此工具。")
    public String searchKnowledgeBase(String query) {
        log.info("Agent 调用工具 [searchKnowledgeBase]: {}", query);
        try {
            // 从 ThreadLocal 获取当前对话关联的文档 ID
            List<UUID> documentIds = AgentContext.getDocumentIds();
            List<Chunk> results = retrievalService.hybridSearch(query, documentIds);

            if (results.isEmpty()) {
                return "未在知识库中找到关于 '" + query + "' 的相关信息。尝试简化关键词或换一种说法。";
            }

            return results.stream()
                    .map(chunk -> String.format("[来源: %s]\n内容: %s", chunk.getSourceMeta(), chunk.getContent()))
                    .collect(Collectors.joining("\n---\n"));
        } catch (Exception e) {
            log.error("工具调用失败", e);
            return "搜索过程中发生错误: " + e.getMessage();
        }
    }

    @Tool("将复杂问题拆解为多个简单的子查询。当用户问题包含多个部分、对比分析或逻辑复杂时使用。返回拆解后的子问题列表。")
    public String decomposeQuery(String query) {
        log.info("Agent 调用工具 [decomposeQuery]: {}", query);
        try {
            List<String> subQueries = decompositionService.decompose(query);
            return "建议将问题拆解为以下子查询进行搜索:\n" + String.join("\n", subQueries);
        } catch (Exception e) {
            log.error("工具调用失败", e);
            return "拆解问题失败: " + e.getMessage();
        }
    }
}
