package com.twocold.jrag.api;

import com.twocold.jrag.api.dto.DocumentDto;
import com.twocold.jrag.api.dto.QaRequest;
import com.twocold.jrag.api.dto.QaResponse;
import com.twocold.jrag.api.dto.UploadResponse;
import com.twocold.jrag.config.CurrentUser;
import com.twocold.jrag.domain.Chunk;
import com.twocold.jrag.domain.Document;
import com.twocold.jrag.ingestion.IngestionService;
import com.twocold.jrag.qa.DeepThinkingAgent;
import com.twocold.jrag.service.DocumentService;
import com.twocold.jrag.service.RetrievalService;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class RAGController {

    private final IngestionService ingestionService;
    private final RetrievalService retrievalService;
    private final DocumentService documentService;
    private final com.twocold.jrag.ingestion.crawler.WebCrawlerService webCrawlerService;
    private final ChatModel chatModel;
    private final DeepThinkingAgent deepThinkingAgent;

    private static final String PROMPT_TEMPLATE = """
            你是一个知识库助手。根据以下文档片段回答用户的问题。不要编造答案。如果无法从片段中找到明确答案，请说“未找到明确答案”。

            用户问题：%s

            文档片段：
            %s

            请基于上面片段回答，并在答案末尾列出每个引用段落对应的来源标识（例如：文件名:页:chunkIndex）。
            """;

    @PostMapping("/ingest-url")
    public ResponseEntity<UploadResponse> ingestUrl(
            @RequestBody com.twocold.jrag.api.dto.UrlIngestRequest request,
            @CurrentUser Long userId) {
        try {
            // 1. 抓取网页内容
            var result = webCrawlerService.fetchAndSave(request.url());
            
            // 2. 创建文档记录 (使用网页标题作为文件名)
            // 注意：我们在文件名后追加 .md 后缀，以确保 DocumentChunkerFactory 能正确选择 MarkdownChunker
            String fileName = result.title() + ".md";
            Document document = documentService.createDocument(fileName, userId, request.isPublic(), "Web", 0L);
            
            // 3. 触发异步入库
            ingestionService.startIngestion(document.getId(), result.tempFile(), userId, request.isPublic());
            
            return ResponseEntity.ok(new UploadResponse(document.getId(), "网页抓取成功，正在后台处理中。", request.isPublic()));
            
        } catch (Exception e) {
            log.error("网页摄取失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new UploadResponse(null, "网页摄取失败: " + e.getMessage(), request.isPublic()));
        }
    }

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
            @RequestParam(name = "category", required = false) String category,
            @CurrentUser Long userId) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new UploadResponse(null, "文件为空", isPublic));
        }
        Path tempFilePath = null; // 在 try-catch 块之外声明，以便 finally 块可以访问它

        try {
            // 1. 创建状态为 PENDING 的文档条目
            Document document = documentService.createDocument(file.getOriginalFilename(), userId, isPublic, category, file.getSize());
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
            List<Chunk> relevantChunks = retrievalService.hybridSearch(req.question(), Collections.emptyList());
            String chunksContext = relevantChunks.isEmpty() ? 
                "" : relevantChunks.stream()
                    .map(Chunk::getContent)
                    .collect(Collectors.joining("\n---\n"));

            String prompt = String.format(PROMPT_TEMPLATE, req.question(), chunksContext);
            String answer = chatModel.chat(prompt);

            return ResponseEntity.ok(new QaResponse(answer, relevantChunks));
        } catch (Exception e) {
            log.error("Query failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/deep-query")
    public ResponseEntity<QaResponse> deepQuery(@RequestBody QaRequest req) {
        try {
            log.info("Received deep query request: {}", req.question());
            // 独立查询端点，手动构造单条消息列表
            List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
            messages.add(UserMessage.from(req.question()));
            
            String answer = deepThinkingAgent.chat(messages);
            return ResponseEntity.ok(new QaResponse(answer, Collections.emptyList()));
        } catch (Exception e) {
            log.error("Deep query failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
