package com.twocold.jrag.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "聊天响应对象 (非流式)")
public class ChatResponse {
    
    @Schema(description = "AI 生成的回答内容")
    private String answer;
    
    @Schema(description = "引用来源的内容片段列表")
    private List<String> sources;
}