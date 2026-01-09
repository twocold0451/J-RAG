package com.twocold.jrag.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "聊天消息对象")
public class ChatMessageDto {
    @Schema(description = "消息 ID")
    private Long id;
    
    @Schema(description = "角色 (USER, ASSISTANT, SYSTEM)")
    private String role; 
    
    @Schema(description = "消息内容")
    private String content;
    
    @Schema(description = "引用来源列表 (JSON 对象或数组)")
    private Object sources;
    
    @Schema(description = "发送时间")
    private LocalDateTime createdAt;
}