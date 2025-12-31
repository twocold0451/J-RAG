package com.twocold.jrag.config;

import com.twocold.jrag.qa.DeepThinkingAgent;
import com.twocold.jrag.qa.RagAgentTools;
import com.twocold.jrag.service.GenericScoringModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * 重排序模型需要手动定义，以便支持多种 API 风格（标准/阿里）
     */
    @Bean
    public ScoringModel scoringModel(RagProperties props) {
        var config = props.retrieval().rerank();
        if (config == null || !config.enabled()) {
            return null;
        }
        return new GenericScoringModel(config);
    }

    @Bean
    public DeepThinkingAgent deepThinkingAgent(ChatModel chatModel, RagAgentTools ragAgentTools) {
        return AiServices.builder(DeepThinkingAgent.class)
                .chatModel(chatModel)
                .tools(ragAgentTools)
                .build();
    }
}
