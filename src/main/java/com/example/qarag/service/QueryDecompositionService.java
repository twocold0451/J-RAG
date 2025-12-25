package com.example.qarag.service;

import com.example.qarag.config.TraceContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryDecompositionService {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    // Regex to extract JSON array
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[.*?]", Pattern.DOTALL);

    private static final String DECOMPOSITION_PROMPT = """
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

    public List<String> decompose(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        if (query.length() < 10) {
            return List.of(query);
        }

        try {
            String prompt = String.format(DECOMPOSITION_PROMPT, query);
            
            TraceContext.setNextGenerationName("LLM: Query Decomposition");
            String response = chatModel.chat(prompt).trim();

            if (response.startsWith("```")) {
                response = response.replaceAll("(?s)^```(json)?|```$", "").trim();
            }

            Matcher matcher = JSON_ARRAY_PATTERN.matcher(response);
            if (matcher.find()) {
                response = matcher.group();
            }

            try {
                List<String> subQueries = objectMapper.readValue(response, new TypeReference<>() {});
                if (subQueries == null || subQueries.isEmpty()) {
                    log.warn("Empty decomposition result, fallback to original.");
                    return List.of(query);
                }
                log.info("Decomposed: '{}' -> {}", query, subQueries);
                return subQueries;
            } catch (Exception e) {
                log.warn("Failed to parse JSON: {}, fallback to original.", response);
                return List.of(query);
            }

        } catch (Exception e) {
            log.error("Decomposition failed", e);
            return List.of(query);
        }
    }
}