package com.twocold.jrag.service;

import com.pgvector.PGvector;
import com.twocold.jrag.domain.Chunk;
import com.twocold.jrag.exception.ResourceNotFoundException;
import com.twocold.jrag.repository.ChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Chunk 管理服务，提供切块的查询、编辑、合并、拆分、删除功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkService {

    private final ChunkRepository chunkRepository;
    private final TextAnalysisService textAnalysisService;

    /**
     * 获取文档的所有切块
     */
    public List<Chunk> getChunksByDocumentId(UUID documentId) {
        return chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
    }

    /**
     * 根据ID获取单个切块
     */
    public Chunk getChunkById(UUID chunkId) {
        return chunkRepository.findById(chunkId)
                .orElseThrow(() -> new ResourceNotFoundException("切块不存在: " + chunkId));
    }

    /**
     * 更新切块内容，重新生成向量和关键词
     */
    @Transactional
    public Chunk updateChunkContent(UUID chunkId, String newContent) {
        Chunk chunk = getChunkById(chunkId);

        if (newContent == null || newContent.isBlank()) {
            throw new IllegalArgumentException("切块内容不能为空");
        }

        // 重新生成向量和关键词
        String keywords = textAnalysisService.extractKeywords(newContent);
        float[] embedding = textAnalysisService.generateEmbedding(newContent);

        // 只更新必要字段，避免 tsvector 字段映射问题
        chunkRepository.updateChunkContent(chunkId, newContent, keywords, new PGvector(embedding));

        // 更新实体对象用于返回
        chunk.setContent(newContent);
        chunk.setContentKeywords(keywords);
        chunk.setContentVector(new PGvector(embedding));

        log.info("更新切块 {} 内容成功，已重新生成向量和关键词", chunkId);

        return chunk;
    }

    /**
     * 合并两个相邻的切块
     *
     * @param chunk1Id 第一个切块ID
     * @param chunk2Id 第二个切块ID
     * @return 合并后的新切块
     */
    @Transactional
    public Chunk mergeChunks(UUID chunk1Id, UUID chunk2Id) {
        Chunk chunk1 = getChunkById(chunk1Id);
        Chunk chunk2 = getChunkById(chunk2Id);

        // 验证属于同一文档
        if (!chunk1.getDocumentId().equals(chunk2.getDocumentId())) {
            throw new IllegalArgumentException("只能合并同一文档的切块");
        }

        // 验证相邻性（chunk2 必须是 chunk1 的下一个）
        if (chunk1.getChunkIndex() + 1 != chunk2.getChunkIndex()) {
            throw new IllegalArgumentException("只能合并相邻的切块");
        }

        UUID documentId = chunk1.getDocumentId();
        int chunk1Index = chunk1.getChunkIndex();

        // 合并内容
        String mergedContent = chunk1.getContent() + "\n" + chunk2.getContent();

        // 重新生成向量和关键词
        String keywords = textAnalysisService.extractKeywords(mergedContent);
        float[] embedding = textAnalysisService.generateEmbedding(mergedContent);

        // 创建新切块（使用 chunk1 的索引）
        Chunk mergedChunk = new Chunk();
        mergedChunk.setId(UUID.randomUUID());
        mergedChunk.setDocumentId(documentId);
        mergedChunk.setContent(mergedContent);
        mergedChunk.setContentVector(new PGvector(embedding));
        mergedChunk.setChunkIndex(chunk1Index);
        mergedChunk.setSourceMeta(chunk1.getSourceMeta()); // 保留 chunk1 的元数据
        mergedChunk.setChunkerName("MANUAL_MERGE");
        mergedChunk.setContentKeywords(keywords);
        mergedChunk.setCreatedAt(OffsetDateTime.now());

        // 删除原有两个切块
        chunkRepository.deleteById(chunk1Id);
        chunkRepository.deleteById(chunk2Id);

        // 更新后续切块的索引（减 1）
        chunkRepository.decrementIndicesAfter(documentId, chunk1Index);

        // 保存新切块（使用原生 SQL 避免类型转换问题）
        UUID newChunkId = UUID.randomUUID();
        chunkRepository.insertChunk(
            newChunkId,
            documentId,
            mergedContent,
            new PGvector(embedding),
            chunk1Index,
            chunk1.getSourceMeta(),
            "MANUAL_MERGE",
            keywords,
            OffsetDateTime.now()
        );

        // 构建返回对象
        mergedChunk.setId(newChunkId);

        log.info("合并切块 {} 和 {} 成功，新切块 ID: {}", chunk1Id, chunk2Id, newChunkId);
        return mergedChunk;
    }

    /**
     * 拆分切块为两部分
     *
     * @param chunkId 原切块ID
     * @param part1   第一部分内容
     * @param part2   第二部分内容
     * @return 包含两个新切块的列表
     */
    @Transactional
    public List<Chunk> splitChunk(UUID chunkId, String part1, String part2) {
        Chunk originalChunk = getChunkById(chunkId);

        if (part1 == null || part1.isBlank() || part2 == null || part2.isBlank()) {
            throw new IllegalArgumentException("拆分后的两部分内容都不能为空");
        }

        UUID documentId = originalChunk.getDocumentId();
        int originalIndex = originalChunk.getChunkIndex();

        // 为两部分分别生成向量和关键词
        String keywords1 = textAnalysisService.extractKeywords(part1);
        float[] embedding1 = textAnalysisService.generateEmbedding(part1);

        String keywords2 = textAnalysisService.extractKeywords(part2);
        float[] embedding2 = textAnalysisService.generateEmbedding(part2);

        // 创建 Chunk 对象用于返回
        Chunk newChunk1 = new Chunk();
        newChunk1.setDocumentId(documentId);
        newChunk1.setContent(part1);
        newChunk1.setContentVector(new PGvector(embedding1));
        newChunk1.setChunkIndex(originalIndex);
        newChunk1.setSourceMeta(originalChunk.getSourceMeta());
        newChunk1.setChunkerName("MANUAL_SPLIT");
        newChunk1.setContentKeywords(keywords1);
        newChunk1.setCreatedAt(OffsetDateTime.now());

        Chunk newChunk2 = new Chunk();
        newChunk2.setDocumentId(documentId);
        newChunk2.setContent(part2);
        newChunk2.setContentVector(new PGvector(embedding2));
        newChunk2.setChunkIndex(originalIndex + 1);
        newChunk2.setSourceMeta(originalChunk.getSourceMeta());
        newChunk2.setChunkerName("MANUAL_SPLIT");
        newChunk2.setContentKeywords(keywords2);
        newChunk2.setCreatedAt(OffsetDateTime.now());

        // 先更新后续切块的索引（加 1），为新 chunk2 腾出空间
        chunkRepository.incrementIndicesFrom(documentId, originalIndex + 1);

        // 生成新切块 ID
        UUID newChunkId1 = UUID.randomUUID();
        UUID newChunkId2 = UUID.randomUUID();

        // 删除原切块
        chunkRepository.deleteById(chunkId);

        // 使用原生 SQL 插入两个新切块
        String sourceMeta = originalChunk.getSourceMeta();
        OffsetDateTime now = OffsetDateTime.now();

        chunkRepository.insertChunk(
            newChunkId1,
            documentId,
            part1,
            new PGvector(embedding1),
            originalIndex,
            sourceMeta,
            "MANUAL_SPLIT",
            keywords1,
            now
        );

        chunkRepository.insertChunk(
            newChunkId2,
            documentId,
            part2,
            new PGvector(embedding2),
            originalIndex + 1,
            sourceMeta,
            "MANUAL_SPLIT",
            keywords2,
            now
        );

        // 设置返回对象的 ID
        newChunk1.setId(newChunkId1);
        newChunk2.setId(newChunkId2);

        log.info("拆分切块 {} 成功，新切块 IDs: {}, {}", chunkId, newChunkId1, newChunkId2);
        return List.of(newChunk1, newChunk2);
    }

    /**
     * 删除单个切块，并重排后续切块的索引
     */
    @Transactional
    public void deleteChunk(UUID chunkId) {
        Chunk chunk = getChunkById(chunkId);
        UUID documentId = chunk.getDocumentId();
        int chunkIndex = chunk.getChunkIndex();

        // 删除切块
        chunkRepository.deleteById(chunkId);

        // 更新后续切块的索引
        chunkRepository.decrementIndicesAfter(documentId, chunkIndex);

        log.info("删除切块 {} 成功，已重排后续索引", chunkId);
    }

    /**
     * 重新分析并更新切块的关键词和向量
     * 用于批量修复或数据迁移
     */
    @Transactional
    public Chunk reanalyzeChunk(UUID chunkId) {
        Chunk chunk = getChunkById(chunkId);

        String content = chunk.getContent();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("切块内容为空，无法重新分析");
        }

        // 重新生成向量和关键词
        String keywords = textAnalysisService.extractKeywords(content);
        float[] embedding = textAnalysisService.generateEmbedding(content);

        chunk.setContentKeywords(keywords);
        chunk.setContentVector(new PGvector(embedding));

        chunkRepository.save(chunk);
        log.info("重新分析切块 {} 成功", chunkId);

        return chunk;
    }
}
