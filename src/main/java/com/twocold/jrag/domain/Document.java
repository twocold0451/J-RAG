package com.twocold.jrag.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Table("documents")
@NoArgsConstructor
@AllArgsConstructor
public class Document implements Persistable<UUID> {
    @Setter
    @Getter
    @Id
    private UUID id;
    @Setter
    @Getter
    private String name;
    @Setter
    @Getter
    private Long userId;
    @Setter
    @Getter
    private OffsetDateTime uploadedAt;
    @Setter
    @Getter
    private boolean isPublic;
    @Setter
    @Getter
    private String category;
    @Setter
    @Getter
    private Long fileSize;
    private DocumentStatus status = DocumentStatus.PENDING; // 具有默认值的新字段
    private Integer progress = 0; // 具有默认值的新字段
    private String errorMessage; // 新字段

    @Setter
    @Transient
    @JsonIgnore
    private boolean isNew = false;

    @Override
    @JsonIgnore
    public boolean isNew() {
        return isNew || id == null;
    }

}