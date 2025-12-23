package com.example.qarag.service;

import com.example.qarag.domain.Document;
import com.example.qarag.domain.DocumentStatus;
import com.example.qarag.repository.DocumentRepository;
import com.example.qarag.repository.UserRepository; // Import UserRepository
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository; // 添加 UserRepository

    public DocumentService(DocumentRepository documentRepository, UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
    }

    public List<Document> getDocumentsForUser(Long userId) {
        return documentRepository.findAllByUserIdOrIsPublicOrderByUploadedAtDesc(userId, true);
    }

    @Transactional
    public Document createDocument(String fileName, Long userId, boolean isPublic) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName(fileName);
        document.setUserId(userId);
        document.setUploadedAt(OffsetDateTime.now());
        document.setPublic(isPublic); // 设置新的 isPublic 字段
        document.setStatus(DocumentStatus.PENDING); // 初始状态
        document.setProgress(0); // 初始进度
        document.setNew(true); // 明确标记为新记录
        return documentRepository.save(document);
    }

    @Transactional
    public void updateDocumentStatusAndProgress(UUID documentId, DocumentStatus status, int progress,
            String errorMessage) {
        documentRepository.findById(documentId).ifPresent(document -> {
            document.setStatus(status);
            document.setProgress(progress);
            document.setErrorMessage(errorMessage);
            documentRepository.save(document);
        });
    }

    private boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(user -> "ADMIN".equals(user.getRole()))
                .orElse(false);
    }

    @Transactional
    public void deleteDocument(UUID documentId, Long userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("未找到文档"));

        boolean isOwner = userId.equals(document.getUserId());
        boolean isAdmin = isAdmin(userId);

        if (!isOwner && !isAdmin) {
            throw new SecurityException("无权访问该文档");
        }

        documentRepository.delete(document);
    }

    @Transactional
    public void toggleDocumentPublicStatus(UUID documentId, boolean isPublic, Long userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("未找到文档"));

        if (!isAdmin(userId)) {
            throw new SecurityException("只有管理员可以管理公共文档");
        }

        document.setPublic(isPublic);
        documentRepository.save(document);
    }
}
