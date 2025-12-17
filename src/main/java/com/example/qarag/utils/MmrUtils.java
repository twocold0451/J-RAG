package com.example.qarag.utils;

import com.example.qarag.domain.Chunk;

import java.util.*;

public class MmrUtils {

    /**
     * Applies Maximal Marginal Relevance (MMR) to re-rank and filter chunks.
     *
     * @param candidates    The list of candidate chunks retrieved from vector search.
     * @param queryVector   The embedding vector of the query.
     * @param k             The number of chunks to select.
     * @param lambda        The diversity parameter (0.0 - 1.0).
     *                      0.5 = Balanced
     *                      1.0 = Pure relevance (Standard Vector Search)
     *                      0.0 = Pure diversity
     * @return A list of selected chunks sorted by MMR score.
     */
    public static List<Chunk> applyMmr(List<Chunk> candidates, float[] queryVector, int k, double lambda) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // Limit k to the number of candidates available
        int limit = Math.min(k, candidates.size());

        List<Chunk> selected = new ArrayList<>(limit);
        List<Chunk> remaining = new ArrayList<>(candidates);

        // Pre-calculate similarity between query and all candidates to avoid re-calculation
        Map<Chunk, Double> querySimilarities = new HashMap<>();
        for (Chunk chunk : candidates) {
            float[] docVector = chunk.getContentVector().toArray();
            double sim = cosineSimilarity(queryVector, docVector);
            querySimilarities.put(chunk, sim);
        }

        while (selected.size() < limit) {
            Chunk bestChunk = null;
            double bestMmrScore = Double.NEGATIVE_INFINITY;

            for (Chunk candidate : remaining) {
                double relevance = querySimilarities.get(candidate);
                double redundancy = 0.0;

                if (!selected.isEmpty()) {
                    double maxSimToSelected = Double.NEGATIVE_INFINITY;
                    float[] candidateVector = candidate.getContentVector().toArray();
                    
                    for (Chunk s : selected) {
                        float[] selectedVector = s.getContentVector().toArray();
                        double sim = cosineSimilarity(candidateVector, selectedVector);
                        if (sim > maxSimToSelected) {
                            maxSimToSelected = sim;
                        }
                    }
                    redundancy = maxSimToSelected;
                }

                // MMR Formula: Lambda * Relevance - (1 - Lambda) * Redundancy
                double mmrScore = (lambda * relevance) - ((1.0 - lambda) * redundancy);

                if (mmrScore > bestMmrScore) {
                    bestMmrScore = mmrScore;
                    bestChunk = candidate;
                }
            }

            if (bestChunk != null) {
                selected.add(bestChunk);
                remaining.remove(bestChunk);
            } else {
                break;
            }
        }

        return selected;
    }

    /**
     * Calculates Cosine Similarity between two vectors.
     * Range: -1 to 1 (1 means identical direction).
     */
    public static double cosineSimilarity(float[] v1, float[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
