package com.example.qarag.qa;

import com.example.qarag.api.dto.QaResponse;
import com.example.qarag.domain.Chunk;

import java.util.List;
import java.util.UUID;

public interface QAService {
    /**
     * Answers a question based on the documents in the knowledge base.
     *
     * @param question The user's question.
     * @return A response containing the answer and the sources used.
     */
    QaResponse answer(String question);

    /**
     * Executes a hybrid search (vector + keyword) for relevant chunks, optionally filtered by document IDs.
     *
     * @param question The user's question.
     * @param topK The number of top relevant chunks to retrieve after RRF fusion.
     * @param documentIds Optional list of document IDs to filter the search by.
     * @return A list of the top K relevant chunks.
     */
    List<Chunk> hybridSearch(String question, int topK, List<UUID> documentIds);
}
