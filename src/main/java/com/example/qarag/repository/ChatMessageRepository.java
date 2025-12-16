package com.example.qarag.repository;

import com.example.qarag.domain.ChatMessage;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends CrudRepository<ChatMessage, Long> {
    List<ChatMessage> findAllByConversationIdOrderByCreatedAtAsc(Long conversationId);

    @Query("SELECT * FROM chat_messages WHERE conversation_id = :conversationId ORDER BY created_at DESC LIMIT :limit")
    List<ChatMessage> findLatestMessagesByConversationId(Long conversationId, int limit);

    @Modifying
    @Query("DELETE FROM chat_messages WHERE conversation_id = :conversationId")
    void deleteByConversationId(Long conversationId);
}
