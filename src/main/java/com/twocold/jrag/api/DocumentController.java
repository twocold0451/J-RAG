package com.twocold.jrag.api;

import com.twocold.jrag.api.dto.DocumentDto;
import com.twocold.jrag.api.dto.UploadResponse;
import com.twocold.jrag.api.dto.UrlIngestRequest;
import com.twocold.jrag.config.CurrentUser;
import com.twocold.jrag.domain.Document;
import com.twocold.jrag.ingestion.IngestionService;
import com.twocold.jrag.ingestion.crawler.WebCrawlerService;
import com.twocold.jrag.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库文档控制器
 * 负责处理文档的上传、网页抓取、列表查询及删除管理。
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "文档管理", description = "用于上传、列出和管理知识库文档的接口。")
public class DocumentController {

    private final IngestionService ingestionService;
    private final DocumentService documentService;
    private final WebCrawlerService webCrawlerService;

    /**
     * 获取当前用户的文档列表
     */
    @Operation(summary = "列出我的文档", description = "获取当前用户已上传的所有文档列表。")
    @GetMapping
    public ResponseEntity<List<DocumentDto>> listDocuments(@CurrentUser Long userId) {
        List<Document> documents = documentService.getDocumentsForUser(userId);
        return ResponseEntity.ok(documents.stream()
                .map(DocumentDto::from)
                .collect(Collectors.toList()));
    }

    /**
     * 上传本地文件并启动异步解析
     */
    @Operation(summary = "上传文件", description = "上传 PDF、DOCX 或 PPT 文件。系统将启动异步后台任务进行分块和向量化。")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<UploadResponse> upload(
            @Parameter(description = "待上传的文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "是否设为公开文档") @RequestParam(name = "isPublic", defaultValue = "false") boolean isPublic,
            @Parameter(description = "可选的分类标签") @RequestParam(name = "category", required = false) String category,
            @CurrentUser Long userId) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new UploadResponse(null, "文件内容为空", isPublic));
        }
        Path tempFilePath;

        try {
            // 1. 创建文档记录（状态为 PENDING）
            Document document = documentService.createDocument(file.getOriginalFilename(), userId, isPublic, category, file.getSize());
            UUID documentId = document.getId();

            // 2. 将上传内容保存到临时文件
            tempFilePath = Files.createTempFile("upload-", "-"+file.getOriginalFilename());
            file.transferTo(tempFilePath.toFile());
            log.info("已为文档 {} 保存临时文件: {}", documentId, tempFilePath);

            // 3. 触发异步摄取任务
            ingestionService.startIngestion(documentId, tempFilePath, userId, isPublic);

            // 4. 立即返回响应
            return ResponseEntity.ok(new UploadResponse(documentId, "文件上传成功，后台处理已启动。", isPublic));
        } catch (Exception e) {
            log.error("启动文件上传/摄取过程时出错", e);
            return ResponseEntity.internalServerError().body(new UploadResponse(null, "启动文档处理失败: " + e.getMessage(), isPublic));
        }
    }

    /**
     * 抓取网页内容并存入知识库
     */
    @Operation(summary = "摄取网页 URL", description = "抓取公开网页内容并自动提取文本存入知识库。")
    @PostMapping("/ingest-url")
    public ResponseEntity<UploadResponse> ingestUrl(
            @RequestBody UrlIngestRequest request,
            @RequestParam(name = "category", required = false) String category,
            @CurrentUser Long userId) {
        try {
            // 1. 抓取 URL 内容
            var result = webCrawlerService.fetchAndSave(request.url());
            
            // 2. 创建文档记录
            String tempFileName = result.tempFile().getFileName().toString();
            String extension = "";
            int lastDotIndex = tempFileName.lastIndexOf('.');
            if (lastDotIndex > 0) {
                extension = tempFileName.substring(lastDotIndex);
            }
            
            String fileName = result.title();
            if (!fileName.toLowerCase().endsWith(extension)) {
                fileName += extension;
            }
            
            Document document = documentService.createDocument(fileName, userId, request.isPublic(), category, 0L);
            
            // 3. 触发异步摄取
            ingestionService.startIngestion(document.getId(), result.tempFile(), userId, request.isPublic());
            
            return ResponseEntity.ok(new UploadResponse(document.getId(), "网页内容抓取已启动。", request.isPublic()));
            
        } catch (Exception e) {
            log.error("网页摄取失败", e);
            return ResponseEntity.internalServerError()
                    .body(new UploadResponse(null, "网页摄取失败: " + e.getMessage(), request.isPublic()));
        }
    }

    /**
     * 删除文档及其关联的向量分块
     */
    @Operation(summary = "删除文档", description = "删除指定的文档及其在向量数据库中关联的所有分块。")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID id,
            @CurrentUser Long userId) {
        documentService.deleteDocument(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 切换文档的公开/私有状态
     */
    @Operation(summary = "切换公开状态", description = "修改文档的可视化权限（公开或仅自己可见）。")
    @PutMapping("/{id}/public")
    public ResponseEntity<Void> togglePublicStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body,
            @CurrentUser Long userId) {
        Boolean isPublic = body.get("isPublic");
        if (isPublic == null) {
            return ResponseEntity.badRequest().build();
        }
        documentService.toggleDocumentPublicStatus(id, isPublic, userId);
        return ResponseEntity.ok().build();
    }
}