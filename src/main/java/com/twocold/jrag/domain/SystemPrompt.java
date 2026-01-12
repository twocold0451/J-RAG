package com.twocold.jrag.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("system_prompts")
public class SystemPrompt {

    @Id
    private String promptKey;
    
    private String promptContent;
    
    private String description;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
