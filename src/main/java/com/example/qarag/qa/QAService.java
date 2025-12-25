package com.example.qarag.qa;

import com.example.qarag.api.dto.QaResponse;
import com.example.qarag.domain.Chunk;

import java.util.List;
import java.util.UUID;

public interface QAService {
    /**
     * 根据知识库中的文档回答问题。
     *
     * @param question 用户的问题。
     * @return 包含答案和所用来源的响应。
     */
    QaResponse answer(String question);

    /**
     * 执行混合搜索（向量 + 关键字）以检索相关片段，可选择按文档 ID 进行过滤。
     *
     * @param question 用户的问题。
     * @param documentIds 用于过滤搜索的可选文档 ID 列表。
     * @return 包含前 K 个相关片段的列表。
     */
    List<Chunk> hybridSearch(String question, List<UUID> documentIds);

    /**
     * 批量执行混合搜索。对每个问题并行执行搜索，然后汇总并去重结果。
     *
     * @param questions 问题列表。
     * @param documentIds 用于过滤搜索的可选文档 ID 列表。
     * @return 汇总后的相关片段列表。
     */
    List<Chunk> batchHybridSearch(List<String> questions, List<UUID> documentIds);
}
