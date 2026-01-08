package com.twocold.jrag.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("rag_interactions")
public class RagInteraction {
    @Id
    private Long id;
    private String traceId;
    private Long conversationId;
    private Long userId;
    private String userQuery;
    private String rewrittenQuery;
    private String aiResponse;
    private String retrievedContexts;
    private LocalDateTime createdAt;
}
