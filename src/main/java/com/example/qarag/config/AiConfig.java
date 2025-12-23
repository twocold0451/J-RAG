package com.example.qarag.config;

import com.example.qarag.service.GenericScoringModel;
import dev.langchain4j.model.scoring.ScoringModel;
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
}
