package com.twocold.jrag.repository;

import com.twocold.jrag.domain.Document;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends CrudRepository<Document, UUID> {
    List<Document> findAllByUserIdOrderByUploadedAtDesc(Long userId);
    List<Document> findAllByUserIdOrIsPublicOrderByUploadedAtDesc(Long userId, boolean isPublic);
}
