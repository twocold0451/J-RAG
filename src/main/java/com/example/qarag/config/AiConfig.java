package com.example.qarag.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;


public class AiConfig {

    // SpEL to read properties, with a default value to avoid startup errors if key is missing
    @Value("${langchain4j.open-ai.chat-model.api-key:YOUR_API_KEY}")
    private String chatApiKey;

    @Value("${langchain4j.open-ai.chat-model.base-url:https://api.openai.com/v1}")
    private String chatBaseUrl;

    @Value("${langchain4j.open-ai.chat-model.model-name:gpt-4o}")
    private String chatModelName;

    @Value("${langchain4j.open-ai.embedding-model.api-key:YOUR_API_KEY}")
    private String embeddingApiKey;

    @Value("${langchain4j.open-ai.embedding-model.base-url:https://api.openai.com/v1}")
    private String embeddingBaseUrl;

    @Value("${langchain4j.open-ai.embedding-model.model-name:text-embedding-3-small}")
    private String embeddingModelName;

    @Bean
    public EmbeddingModel embeddingModel() {
        if (embeddingApiKey == null || embeddingApiKey.equals("YOUR_API_KEY") || embeddingApiKey.isBlank()) {
            throw new IllegalArgumentException("API Key for Embedding Model is not configured. Please check your application.properties file.");
        }
        return OpenAiEmbeddingModel.builder()
                .apiKey(embeddingApiKey)
                .baseUrl(embeddingBaseUrl)
                .modelName(embeddingModelName)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public ChatModel chatLanguageModel() {
        if (chatApiKey == null || chatApiKey.equals("YOUR_API_KEY") || chatApiKey.isBlank()) {
            throw new IllegalArgumentException("API Key for Chat Model is not configured. Please check your application.properties file.");
        }
        return OpenAiChatModel.builder()
                .apiKey(chatApiKey)
                .baseUrl(chatBaseUrl)
                .modelName(chatModelName)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public StreamingChatModel streamingChatLanguageModel() {
        if (chatApiKey == null || chatApiKey.equals("YOUR_API_KEY") || chatApiKey.isBlank()) {
            throw new IllegalArgumentException("API Key for Streaming Chat Model is not configured.");
        }
        return OpenAiStreamingChatModel.builder()
                .apiKey(chatApiKey)
                .baseUrl(chatBaseUrl)
                .modelName(chatModelName)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}