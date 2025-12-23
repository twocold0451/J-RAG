package com.example.qarag.api;

import com.example.qarag.api.dto.DocumentDto;
import com.example.qarag.api.dto.QaRequest;
import com.example.qarag.api.dto.QaResponse;
import com.example.qarag.api.dto.UploadResponse;
import com.example.qarag.config.CurrentUser;
import com.example.qarag.domain.Document;
import com.example.qarag.ingestion.IngestionService;
import com.example.qarag.qa.QAService;
import com.example.qarag.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Add this import
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files; // Add this import
import java.nio.file.Path; // Add this import
import java.util.List;
import java.util.Map; // Add this import
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j // 添加该注解
public class RAGController {

    private final IngestionService ingestionService;
    private final QAService qaService;
    private final DocumentService documentService;

    @PutMapping("/documents/{id}/public")
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

    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "isPublic", defaultValue = "false") boolean isPublic,
            @CurrentUser Long userId) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new UploadResponse(null, "文件为空", isPublic));
        }
        Path tempFilePath = null; // 在 try-catch 块之外声明，以便 finally 块可以访问它

        try {
            // 1. 创建状态为 PENDING 的文档条目
            Document document = documentService.createDocument(file.getOriginalFilename(), userId, isPublic);
            UUID documentId = document.getId();

            // 2. 将 MultipartFile 保存到临时文件
            tempFilePath = Files.createTempFile("upload-", file.getOriginalFilename());
            file.transferTo(tempFilePath.toFile());
            log.info("已为文档 {} 保存临时文件: {}", documentId, tempFilePath);

            // 3. 触发异步解析入库
            ingestionService.startIngestion(documentId, tempFilePath, userId, isPublic);

            // 4. 返回即时响应
            return ResponseEntity.ok(new UploadResponse(documentId, "文件上传已成功启动。后台处理中。", isPublic));
        } catch (Exception e) {
            log.error("文件上传和解析启动过程中出错: {}", e.getMessage(), e);
            // 如果在异步解析开始之前发生错误，将文档状态更新为 FAILED
            // 或者如果文档创建失败，则进行优雅处理
            return ResponseEntity.internalServerError().body(new UploadResponse(null, "启动文档处理出错: " + e.getMessage(), isPublic));
        }
    }

    @GetMapping("/documents")
    public ResponseEntity<List<DocumentDto>> listDocuments(@CurrentUser Long userId) {
        List<Document> documents = documentService.getDocumentsForUser(userId);
        return ResponseEntity.ok(documents.stream()
                .map(DocumentDto::from)
                .collect(Collectors.toList()));
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID id,
            @CurrentUser Long userId) {
        documentService.deleteDocument(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/query")
    public ResponseEntity<QaResponse> query(@RequestBody QaRequest req) {
        try {
            QaResponse resp = qaService.answer(req.question());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            // 在实际应用中应正确记录异常日志
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
