package com.twocold.jrag.domain;

import com.pgvector.PGvector;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

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



    /**

     * 检索评分 (仅用于内存排序，不持久化)

     */

    @org.springframework.data.annotation.Transient

    private Double score;

}
