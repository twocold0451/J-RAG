package com.example.qarag.service;

import com.example.qarag.config.RagProperties;
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
            You are a query analysis expert. Decompose the user's complex question into simple, independent sub-queries for RAG retrieval.
            
            Original Question: %s
            
            Rules:
            1. If the question is simple, return a list containing just the original question.
            2. Split complex questions (comparisons, multi-part) into independent **fact-retrieval** queries.
            3. **Do not generate analytical or "why" questions.** Focus on retrieving the underlying data or facts needed to answer.
            4. **Limit to maximum 3 sub-queries.** Quality over quantity.
            5. Sub-queries must be complete sentences with explicit entities.
            6. Output strictly a JSON array of strings.
            
            Example 1 (Comparison):
            Input: What is the difference between Apple and Banana?
            Output: ["What are the features of Apple", "What are the features of Banana"]
            
            Example 2 (Multi-part):
            Input: Docker advantages and installation?
            Output: ["Docker advantages", "Docker installation steps"]
            
            JSON Output:
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