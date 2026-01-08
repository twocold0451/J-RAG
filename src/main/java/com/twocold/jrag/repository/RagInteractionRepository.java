package com.twocold.jrag.repository;

import com.twocold.jrag.domain.RagInteraction;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RagInteractionRepository extends CrudRepository<RagInteraction, Long> {
    List<RagInteraction> findByConversationIdOrderByCreatedAtDesc(Long conversationId);
}
