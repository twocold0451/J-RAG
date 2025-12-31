package com.twocold.jrag.repository;

import com.twocold.jrag.domain.TemplateDocument;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemplateDocumentRepository extends CrudRepository<TemplateDocument, Long> {
    List<TemplateDocument> findByTemplateId(Long templateId);
    
    @Modifying
    @Query("DELETE FROM template_documents WHERE template_id = :templateId")
    void deleteByTemplateId(Long templateId);
}
