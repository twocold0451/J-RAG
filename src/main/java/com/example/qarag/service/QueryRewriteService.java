package com.example.qarag.service;

import com.example.qarag.config.RagProperties;
import com.example.qarag.domain.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriteService {

    private final ChatModel chatModel;
    private final RagProperties ragProperties;

    // 判断：包含这些代词、长度较短或包含常见搜索噪声词时，建议重写或优化
    private static final Pattern REWRITE_INDICATORS = Pattern.compile(
            ".*(它|他|她|这|那|其|前述|刚才|之前|这个|那个|具体的|原理|详细|解释下|请问|我想知道|能否|告诉我).*",
            Pattern.CASE_INSENSITIVE
    );

    private static final String REWRITE_PROMPT = """
            分析用户当前的问题，并结合对话历史（如果有）进行处理。
            
            任务：
            1. **补全上下文**：如果当前问题依赖上下文（包含代词、主题省略等），请补全缺失信息，使其成为独立的搜索语句。
            2. **搜索去噪（关键）**：过滤掉对全文检索无意义的噪声。包括：
               - 礼貌用语（如“请问”、“你好”、“麻烦帮我”）。
               - 冗余谓语（如“我想了解”、“分析一下”、“解释一下”、“告诉我”）。
               - 语气词和助词（如“吧”、“吗”、“呢”、“的”、“了”）。
               - 无意义的标点符号。
            3. **核心保留**：仅保留具有实际检索价值的实体、名词、动作等核心词汇。
            
            限制：
            - 仅输出处理后的搜索短语或关键词，不要包含任何解释。
            - 保持原意，不要过度解读。
            - 如果无需处理，请原样输出原始问题。
            
            对话历史：
            %s
            
            当前用户问题：
            %s
            
            处理后的搜索结果（仅输出结果）：
            """;

    /**
     * 根据上下文智能重写查询，并进行搜索去噪优化
     */
    public String rewriteIfNecessary(String query, List<ChatMessage> history) {
        if (ragProperties.retrieval().rewrite() == null || !ragProperties.retrieval().rewrite().enabled()) {
            return query;
        }

        // 1. 判断是否值得调用 LLM 处理
        if (!shouldProcessQuery(query, history)) {
            log.debug("查询 '{}' 不需要重写或去噪", query);
            return query;
        }

        // 2. LLM 智能重写与去噪
        try {
            String historyContext = (history == null || history.isEmpty()) 
                    ? "无" 
                    : history.stream()
                        .map(msg -> String.format("%s: %s", msg.getRole(), msg.getContent()))
                        .collect(Collectors.joining("\n"));

            String prompt = String.format(REWRITE_PROMPT, historyContext, query);
            String processedQuery = chatModel.chat(prompt).trim();

            // 去掉可能的引号（LLM 有时会加上）
            processedQuery = processedQuery.replaceAll("^[\"']|[\"']$", "");

            if (processedQuery.equalsIgnoreCase(query)) {
                log.debug("LLM 决定查询 '{}' 不需要改动", query);
            } else {
                log.info("查询已优化：'{}' -> '{}'", query, processedQuery);
            }
            return processedQuery.isEmpty() ? query : processedQuery;
        } catch (Exception e) {
            log.error("通过 LLM 优化查询失败，回退到原始查询", e);
            return query;
        }
    }

    private boolean shouldProcessQuery(String query, List<ChatMessage> history) {
        if (query == null || query.isBlank()) return false;
        
        // 1. 如果有历史记录，通常需要检查上下文一致性
        if (history != null && !history.isEmpty()) return true;

        // 2. 如果查询包含特定的指示词（代词或常见去噪目标）
        if (REWRITE_INDICATORS.matcher(query).matches()) return true;

        // 3. 如果查询太短（可能需要补全）或较长（可能包含大量噪声）
        return query.length() < 5 || query.length() > 20;
    }
}
