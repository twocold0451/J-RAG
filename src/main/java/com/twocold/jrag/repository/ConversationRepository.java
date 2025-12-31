package com.twocold.jrag.repository;

import com.twocold.jrag.domain.Conversation;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends CrudRepository<Conversation, Long> {
    List<Conversation> findAllByUserIdOrderByUpdatedAtDesc(Long userId);

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    Optional<Conversation> findConversationById(Long id);

    @Query("SELECT * FROM conversations WHERE user_id = :userId OR (is_public = true AND (allowed_users IS NULL OR allowed_users = '' OR allowed_users LIKE '%' || :username || '%')) ORDER BY updated_at DESC")
    List<Conversation> findAllVisibleConversations(Long userId, String username);
}
