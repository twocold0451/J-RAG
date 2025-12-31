package com.twocold.jrag.repository;

import com.twocold.jrag.domain.Chunk;
import com.pgvector.PGvector;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChunkRepository extends CrudRepository<Chunk, UUID> {

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
}
