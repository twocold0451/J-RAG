package com.twocold.jrag.api;

import com.twocold.jrag.domain.Chunk;
import com.twocold.jrag.service.ChunkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chunk 管理 API 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/chunks")
@RequiredArgsConstructor
@Tag(name = "Chunk Management", description = "文档切块管理接口")
public class ChunkController {

    private final ChunkService chunkService;

    /**
     * 获取指定文档的所有切块
     */
    @GetMapping("/document/{documentId}")
    @Operation(summary = "获取文档的切块列表", description = "获取指定文档的所有切块，按 chunk_index 升序排列")
    public ResponseEntity<List<Chunk>> getChunksByDocumentId(
            @Parameter(description = "文档ID") @PathVariable UUID documentId) {
        log.info("获取文档 {} 的切块列表", documentId);
        List<Chunk> chunks = chunkService.getChunksByDocumentId(documentId);
        return ResponseEntity.ok(chunks);
    }

    /**
     * 获取单个切块详情
     */
    @GetMapping("/{chunkId}")
    @Operation(summary = "获取切块详情", description = "获取单个切块的详细信息")
    public ResponseEntity<Chunk> getChunkById(
            @Parameter(description = "切块ID") @PathVariable UUID chunkId) {
        log.info("获取切块 {} 详情", chunkId);
        Chunk chunk = chunkService.getChunkById(chunkId);
        return ResponseEntity.ok(chunk);
    }

    /**
     * 更新切块内容
     */
    @PutMapping("/{chunkId}")
    @Operation(summary = "更新切块内容", description = "更新切块内容，并自动重新生成向量和关键词")
    public ResponseEntity<Chunk> updateChunkContent(
            @Parameter(description = "切块ID") @PathVariable UUID chunkId,
            @RequestBody Map<String, String> request) {
        String newContent = request.get("content");
        log.info("更新切块 {} 内容", chunkId);
        Chunk updatedChunk = chunkService.updateChunkContent(chunkId, newContent);
        return ResponseEntity.ok(updatedChunk);
    }

    /**
     * 合并两个切块
     */
    @PostMapping("/merge")
    @Operation(summary = "合并两个切块", description = "将两个相邻的切块合并为一个，自动重新生成向量和关键词")
    public ResponseEntity<Chunk> mergeChunks(@RequestBody MergeChunksRequest request) {
        log.info("合并切块 {} 和 {}", request.chunk1Id(), request.chunk2Id());
        Chunk mergedChunk = chunkService.mergeChunks(request.chunk1Id(), request.chunk2Id());
        return ResponseEntity.ok(mergedChunk);
    }

    /**
     * 拆分切块
     */
    @PostMapping("/{chunkId}/split")
    @Operation(summary = "拆分切块", description = "将一个切块拆分为两个，自动分别为两部分生成向量和关键词")
    public ResponseEntity<List<Chunk>> splitChunk(
            @Parameter(description = "切块ID") @PathVariable UUID chunkId,
            @RequestBody SplitChunkRequest request) {
        log.info("拆分切块 {} ", chunkId);
        List<Chunk> newChunks = chunkService.splitChunk(chunkId, request.part1(), request.part2());
        return ResponseEntity.ok(newChunks);
    }

    /**
     * 删除切块
     */
    @DeleteMapping("/{chunkId}")
    @Operation(summary = "删除切块", description = "删除单个切块，并自动重排后续切块的索引")
    public ResponseEntity<Void> deleteChunk(
            @Parameter(description = "切块ID") @PathVariable UUID chunkId) {
        log.info("删除切块 {}", chunkId);
        chunkService.deleteChunk(chunkId);
        return ResponseEntity.ok().build();
    }

    /**
     * 重新分析切块
     */
    @PostMapping("/{chunkId}/reanalyze")
    @Operation(summary = "重新分析切块", description = "重新生成切块的向量和关键词")
    public ResponseEntity<Chunk> reanalyzeChunk(
            @Parameter(description = "切块ID") @PathVariable UUID chunkId) {
        log.info("重新分析切块 {}", chunkId);
        Chunk chunk = chunkService.reanalyzeChunk(chunkId);
        return ResponseEntity.ok(chunk);
    }

    // ==================== DTO Records ====================

    public record MergeChunksRequest(UUID chunk1Id, UUID chunk2Id) {}

    public record SplitChunkRequest(String part1, String part2) {}
}
