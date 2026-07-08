package com.twocold.jrag.service;

import com.twocold.jrag.agent.SubQuery;
import com.twocold.jrag.agent.DecompositionResult;
import com.twocold.jrag.config.Observed;
import com.twocold.jrag.config.TraceContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 查询分解服务
 * 负责将复杂的提问拆解为多个简单的子查询，以便在 RAG 系统中获取更全面、精准的事实数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryDecompositionService {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final PromptService promptService;

    // 正则表达式用于提取 JSON 数组
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[.*?]", Pattern.DOTALL);

    private static final String DEFAULT_DECOMPOSITION_PROMPT = """
            你是一位查询分析专家。请将用户复杂的提问拆解为简单、独立的子查询，以便于在 RAG 系统中进行检索。
            
            原始问题: %s
            
            规则：
            1. 如果问题很简单（单一意图），请直接返回包含原始问题的列表。
            2. 将复杂问题（如对比分析、多步推理）拆解为独立的**事实检索型**子查询。
            3. **不要生成分析型或反思型问题（如“为什么”、“怎么看待”）。** 专注于检索回答问题所需的基础事实或数据。
            4. **限制最多生成 3 个子查询。** 宁缺毋滥，精准第一。
            5. 子查询必须是包含了明确主语和实体的完整句子（消除指代不明）。
            6. **必须且仅输出简体中文**，因为知识库是中文的。
            7. 严格输出一个字符串 JSON 数组，不要包含 Markdown 代码块或其他解释。
            
            示例 1 (对比):
            输入: 农村和城镇人均消费支出增长区别
            输出: ["农村人均消费支出的增长情况是什么", "城镇人均消费支出的增长情况是什么"]
            
            示例 2 (多部分):
            输入: Docker的优势和安装步骤
            输出: ["Docker的优势", "Docker的安装步骤"]
            
            JSON 输出:
            """;

    private static final String DEPENDENCY_AWARE_DECOMPOSITION_PROMPT = """
            你是一个问题分析专家。请将用户的复杂问题拆解为多个可并行或顺序执行的子查询。

            原始问题: %s

            【拆解规则】
            1. 如果问题很简单（单一意图），直接返回包含原问题的一个子查询。
            2. 将复杂问题拆解为独立的**事实检索型**子查询，避免"为什么"、"怎么看待"等分析型问题。
            3. **限制最多生成 3 个子查询。**
            4. 子查询必须是包含明确主语和实体的完整句子（消除指代不明）。
            5. **必须且仅输出简体中文**。

            【依赖关系判断】
            分析子问题间的依赖关系：
            - **独立型（independent）**：不依赖其他子问题的结果，可直接并行搜索
            - **依赖型（dependent）**：需要其他子问题的结果作为查询条件或背景信息

            【依赖判断标准】
            - 子问题B依赖子问题A，如果B的查询需要用到A的答案中的**具体数值、实体名称、时间、状态**等
            - 例如："利润率变化" 依赖 "成本变化数据"；"推荐工具" 依赖 "技术栈信息"

            【输出格式】
            请以JSON数组格式输出，每个子查询包含以下字段：
            - id: 唯一标识（如 "q1", "q2", "q3"）
            - query: 子查询内容（中文，完整句子）
            - dependsOn: 依赖的子问题ID列表（无依赖则为空数组 []）
            - type: 类型（"independent" 或 "dependent"）
            - reason: 简要说明为什么有这些依赖（可选）

            示例 1（无依赖）：
            输入: "Docker的优势和安装步骤"
            输出:
            [
              {
                "id": "q1",
                "query": "Docker的优势是什么",
                "dependsOn": [],
                "type": "independent",
                "reason": "基础信息，可直接查询"
              },
              {
                "id": "q2",
                "query": "Docker的安装步骤是什么",
                "dependsOn": [],
                "type": "independent",
                "reason": "基础信息，可直接查询"
              }
            ]

            示例 2（有依赖）：
            输入: "分析云原生架构对IT成本和利润率的影响"
            输出:
            [
              {
                "id": "q1",
                "query": "该公司在采用云原生架构前的IT基础架构和年度IT成本构成是什么",
                "dependsOn": [],
                "type": "independent",
                "reason": "基础信息，无需前置依赖"
              },
              {
                "id": "q2",
                "query": "该公司采用云原生架构后，IT成本在计算资源、运维人力等方面发生了哪些具体变化",
                "dependsOn": ["q1"],
                "type": "dependent",
                "reason": "需要q1的原成本数据作为对比基准"
              },
              {
                "id": "q3",
                "query": "基于IT成本变化，公司季度利润率的增减情况及成本对利润的具体影响百分比是多少",
                "dependsOn": ["q2"],
                "type": "dependent",
                "reason": "需要q2的成本变化数据才能计算利润影响"
              }
            ]

            注意：
            - 必须形成无环依赖图（DAG），不要出现循环依赖（如 q1依赖q2，q2又依赖q1）
            - 若子查询之间无明显依赖，全部设为 independent，并行执行
            - 严格输出JSON数组，不要包含 Markdown 代码块或其他解释

            """;

    /**
     * 将复杂查询分解为子查询列表
     * 
     * @param query 原始用户查询
     * @return 分解后的子查询列表
     */
    @Observed(name = "Query Decomposition")
    public List<String> decompose(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        if (query.length() < 10) {
            return List.of(query);
        }

        try {
            String template = promptService.getPrompt("query_decomposition", DEFAULT_DECOMPOSITION_PROMPT);
            String prompt = String.format(template, query);
            
            TraceContext.setNextGenerationName("LLM: Query Decomposition");
            String response = chatModel.chat(prompt).trim();

            // 1. 清理可能的 Markdown 标记
            String jsonContent = response;
            if (jsonContent.contains("```")) {
                // 尝试提取 ```json ... ``` 或 ``` ... ``` 之间的内容
                Matcher codeBlockMatcher = Pattern.compile("```(?:json)?\\s*(.*?)\\s*```", Pattern.DOTALL).matcher(jsonContent);
                if (codeBlockMatcher.find()) {
                    jsonContent = codeBlockMatcher.group(1).trim();
                }
            }

            // 2. 如果清理后还是不像数组，再尝试用正则提取第一个 [ ] 块
            if (!jsonContent.startsWith("[")) {
                Matcher matcher = JSON_ARRAY_PATTERN.matcher(jsonContent);
                if (matcher.find()) {
                    jsonContent = matcher.group();
                }
            }

            try {
                log.debug("Processed response before parsing: {}", jsonContent);
                List<String> subQueries = objectMapper.readValue(jsonContent, new TypeReference<>() {});
                if (subQueries == null || subQueries.isEmpty()) {
                    log.error("Empty decomposition result, fallback to original.");
                    return List.of(query);
                }
                log.info("Decomposed: '{}' -> {}", query, subQueries);
                return subQueries;
            } catch (Exception e) {
                log.error("Failed to parse JSON: {}, fallback to original.", response);
                return List.of(query);
            }

        } catch (Exception e) {
            log.error("Decomposition failed", e);
            return List.of(query);
        }
    }

    /**
     * 将复杂查询分解为带依赖关系的子查询
     * LLM会分析子问题间的依赖关系，生成DAG结构
     *
     * @param query 原始用户查询
     * @return 分解结果，包含子查询列表和依赖关系
     */
    @Observed(name = "Query Decomposition with Dependencies")
    public DecompositionResult decomposeWithDependencies(String query) {
        if (query == null || query.isBlank()) {
            return new DecompositionResult();
        }

        if (query.length() < 10) {
            SubQuery sq = new SubQuery();
            sq.setId("q1");
            sq.setQuery(query);
            sq.setType("independent");
            sq.setDependsOn(List.of());
            sq.setReason("简单问题，无需拆解");
            return new DecompositionResult(List.of(sq));
        }

        try {
            String template = promptService.getPrompt("query_decomposition_with_deps", DEPENDENCY_AWARE_DECOMPOSITION_PROMPT);
            String prompt = String.format(template, query);

            TraceContext.setNextGenerationName("LLM: Query Decomposition with Dependencies");
            String response = chatModel.chat(prompt).trim();

            // 1. 清理可能的 Markdown 标记
            String jsonContent = response;
            if (jsonContent.contains("```")) {
                Matcher codeBlockMatcher = Pattern.compile("```(?:json)?\\s*(.*?)\\s*```", Pattern.DOTALL).matcher(jsonContent);
                if (codeBlockMatcher.find()) {
                    jsonContent = codeBlockMatcher.group(1).trim();
                }
            }

            // 2. 如果清理后还是不像数组，再尝试用正则提取第一个 [ ] 块
            if (!jsonContent.startsWith("[")) {
                Matcher matcher = JSON_ARRAY_PATTERN.matcher(jsonContent);
                if (matcher.find()) {
                    jsonContent = matcher.group();
                }
            }

            try {
                log.debug("Processed response before parsing: {}", jsonContent);
                List<SubQuery> subQueries = objectMapper.readValue(jsonContent, new TypeReference<List<SubQuery>>() {});

                if (subQueries == null || subQueries.isEmpty()) {
                    log.error("Empty decomposition result, fallback to original.");
                    SubQuery sq = new SubQuery();
                    sq.setId("q1");
                    sq.setQuery(query);
                    sq.setType("independent");
                    sq.setDependsOn(List.of());
                    sq.setReason("解析失败，使用原问题");
                    return new DecompositionResult(List.of(sq));
                }

                // 验证并补充ID（如果LLM没生成）
                for (int i = 0; i < subQueries.size(); i++) {
                    SubQuery sq = subQueries.get(i);
                    if (sq.getId() == null || sq.getId().isBlank()) {
                        sq.setId("q" + (i + 1));
                    }
                    if (sq.getDependsOn() == null) {
                        sq.setDependsOn(new ArrayList<>());
                    }
                    if (sq.getType() == null) {
                        sq.setType(sq.getDependsOn().isEmpty() ? "independent" : "dependent");
                    }
                }

                log.info("Decomposed with dependencies: '{}' -> {}", query, subQueries);
                return new DecompositionResult(subQueries);

            } catch (Exception e) {
                log.error("Failed to parse JSON with dependencies: {}, fallback to original.", response, e);
                SubQuery sq = new SubQuery();
                sq.setId("q1");
                sq.setQuery(query);
                sq.setType("independent");
                sq.setDependsOn(List.of());
                sq.setReason("解析失败，使用原问题");
                return new DecompositionResult(List.of(sq));
            }

        } catch (Exception e) {
            log.error("Decomposition with dependencies failed", e);
            SubQuery sq = new SubQuery();
            sq.setId("q1");
            sq.setQuery(query);
            sq.setType("independent");
            sq.setDependsOn(List.of());
            sq.setReason("异常，使用原问题");
            return new DecompositionResult(List.of(sq));
        }
    }
}