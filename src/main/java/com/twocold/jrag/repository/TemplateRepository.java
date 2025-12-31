package com.twocold.jrag.repository;

import com.twocold.jrag.domain.Template;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemplateRepository extends CrudRepository<Template, Long> {
    
    // Find all public templates
    List<Template> findByIsPublicTrue();
    
    // Find templates created by user
    List<Template> findByUserId(Long userId);

    @Query("SELECT * FROM templates t WHERE t.is_public = true AND (t.visible_groups IS NULL OR t.visible_groups::jsonb ?| ARRAY(SELECT ugm.group_id::text FROM user_group_members ugm WHERE ugm.user_id = :userId))")
    List<Template> findVisibleTemplatesForUser(@Param("userId") Long userId);
}
