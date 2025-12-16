package com.example.qarag.domain;

import com.pgvector.PGvector;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Setter
@Getter
@Data
@Table("chunks")
@NoArgsConstructor
@AllArgsConstructor
public class Chunk {
    @Id
    private UUID id;
    private UUID documentId;
    private String content;
    private PGvector contentVector;
    private int chunkIndex;
    private String sourceMeta;
    private String chunkerName;
    private String contentKeywords;
    private OffsetDateTime createdAt;

}