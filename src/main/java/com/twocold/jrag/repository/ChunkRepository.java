package com.twocold.jrag.repository;

import com.twocold.jrag.domain.Chunk;
import com.pgvector.PGvector;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChunkRepository extends CrudRepository<Chunk, UUID> {

    /**
     * 根据文档ID查询所有切块，按 chunk_index 升序排列
     */
    @Query("SELECT * FROM chunks WHERE document_id = :documentId ORDER BY chunk_index ASC")
    List<Chunk> findByDocumentIdOrderByChunkIndexAsc(@Param("documentId") UUID documentId);

    /**
     * 查询文档的切块数量
     */
    @Query("SELECT COUNT(*) FROM chunks WHERE document_id = :documentId")
    int countByDocumentId(@Param("documentId") UUID documentId);

    /**
     * 更新指定 chunk_index 之后的所有切块的索引（用于删除、合并后重排）
     * 将 index > startIndex 的切块索引减 1
     */
    @Modifying
    @Query("UPDATE chunks SET chunk_index = chunk_index - 1 WHERE document_id = :documentId AND chunk_index > :startIndex")
    void decrementIndicesAfter(@Param("documentId") UUID documentId, @Param("startIndex") int startIndex);

    /**
     * 更新指定 chunk_index 之后的所有切块的索引（用于拆分后重排）
     * 将 index >= startIndex 的切块索引加 1
     */
    @Modifying
    @Query("UPDATE chunks SET chunk_index = chunk_index + 1 WHERE document_id = :documentId AND chunk_index >= :startIndex")
    void incrementIndicesFrom(@Param("documentId") UUID documentId, @Param("startIndex") int startIndex);

    /**
     * 根据文档ID和索引范围查询切块
     */
    @Query("SELECT * FROM chunks WHERE document_id = :documentId AND chunk_index BETWEEN :startIndex AND :endIndex ORDER BY chunk_index ASC")
    List<Chunk> findByDocumentIdAndChunkIndexBetween(@Param("documentId") UUID documentId, @Param("startIndex") int startIndex, @Param("endIndex") int endIndex);

    /**
     * 删除文档的所有切块
     */
    @Query("DELETE FROM chunks WHERE document_id = :documentId")
    void deleteByDocumentId(@Param("documentId") UUID documentId);

    /**
     * Finds the top k chunks with content vectors closest to the given query vector.
     * The distance metric used is cosine distance (vector_cosine_ops).
     *
     * @param queryVector The embedding vector of the user's query.
     * @param topK        The number of nearest neighbors to retrieve.
     * @return A list of the top k closest chunks.
     */
    @Query("SELECT * FROM chunks ORDER BY content_vector <=> :queryVector LIMIT :topK")
    List<Chunk> findNearestNeighbors(
            @Param("queryVector") PGvector queryVector,
            @Param("topK") int topK
    );

    @Query("SELECT * FROM chunks WHERE document_id IN (:documentIds) ORDER BY content_vector <=> :queryVector LIMIT :topK")
    List<Chunk> findNearestNeighborsByDocumentIds(
            @Param("queryVector") PGvector queryVector,
            @Param("topK") int topK,
            @Param("documentIds") List<UUID> documentIds
    );
    @Modifying
    @Query("UPDATE chunks SET content = :content, content_keywords = :contentKeywords, content_vector = :contentVector WHERE id = :chunkId")
    void updateChunkContent(@Param("chunkId") UUID chunkId,
                            @Param("content") String content,
                            @Param("contentKeywords") String contentKeywords,
                            @Param("contentVector") PGvector contentVector);
    @Modifying
    @Query("INSERT INTO chunks(id, document_id, content, content_vector, chunk_index, source_meta, chunker_name, content_keywords, created_at) " +
           "VALUES (:id, :documentId, :content, :contentVector, :chunkIndex, CAST(:sourceMeta AS jsonb), :chunkerName, :contentKeywords, :createdAt)")
    void insertChunk(@Param("id") UUID id,
                     @Param("documentId") UUID documentId,
                     @Param("content") String content,
                     @Param("contentVector") PGvector contentVector,
                     @Param("chunkIndex") int chunkIndex,
                     @Param("sourceMeta") String sourceMeta,
                     @Param("chunkerName") String chunkerName,
                     @Param("contentKeywords") String contentKeywords,
                     @Param("createdAt") OffsetDateTime createdAt);
}
