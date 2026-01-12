package com.twocold.jrag.repository;

import com.twocold.jrag.domain.SystemPrompt;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemPromptRepository extends CrudRepository<SystemPrompt, String> {
}
