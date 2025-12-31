package com.twocold.jrag.utils;

import com.twocold.jrag.domain.Chunk;

import java.util.*;

public class MmrUtils {

    /**
     * 应用最大边缘相关性 (MMR) 来重新排序和过滤片段。
     *
     * @param candidates    从向量搜索中检索到的候选片段列表。
     * @param queryVector   查询的嵌入向量。
     * @param k             要选择的片段数量。
     * @param lambda        多样性参数 (0.0 - 1.0)。
     *                      0.5 = 平衡
     *                      1.0 = 纯相关性 (标准向量搜索)
     *                      0.0 = 纯多样性
     * @return 按 MMR 分数排序的所选片段列表。
     */
    public static List<Chunk> applyMmr(List<Chunk> candidates, float[] queryVector, int k, double lambda) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 限制 k 的数量不超过候选片段的总数
        int limit = Math.min(k, candidates.size());

        List<Chunk> selected = new ArrayList<>(limit);
        List<Chunk> remaining = new ArrayList<>(candidates);

        // 预先计算查询与所有候选片段之间的相似度，以避免重复计算
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

                // MMR 公式: Lambda * 相关性 - (1 - Lambda) * 冗余度
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
     * 计算两个向量之间的余弦相似度。
     * 范围：-1 到 1（1 表示方向完全相同）。
     */
    public static double cosineSimilarity(float[] v1, float[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("向量长度必须相同");
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
