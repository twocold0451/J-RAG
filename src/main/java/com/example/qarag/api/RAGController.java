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
@Slf4j // Add this annotation
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
            return ResponseEntity.badRequest().body(new UploadResponse(null, "File is empty", isPublic));
        }
        Path tempFilePath = null; // Declare outside try-catch to ensure finally block can access it

        try {
            // 1. Create a Document entry with PENDING status
            Document document = documentService.createDocument(file.getOriginalFilename(), userId, isPublic);
            UUID documentId = document.getId();

            // 2. Save the MultipartFile to a temporary file
            tempFilePath = Files.createTempFile("upload-", file.getOriginalFilename());
            file.transferTo(tempFilePath.toFile());
            log.info("Saved temporary file for document {}: {}", documentId, tempFilePath);

            // 3. Trigger asynchronous ingestion
            ingestionService.startIngestion(documentId, tempFilePath, userId, isPublic);

            // 4. Return immediate response
            return ResponseEntity.ok(new UploadResponse(documentId, "File upload initiated successfully. Processing in background.", isPublic));
        } catch (Exception e) {
            log.error("Error during file upload and ingestion initiation: {}", e.getMessage(), e);
            // If an error occurs before async ingestion starts, update document status to FAILED
            // Or if document creation fails, handle it gracefully
            return ResponseEntity.internalServerError().body(new UploadResponse(null, "Error initiating document processing: " + e.getMessage(), isPublic));
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
            // Log the exception properly in a real application
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
