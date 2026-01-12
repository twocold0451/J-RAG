package com.twocold.jrag.service;

import com.twocold.jrag.domain.SystemPrompt;
import com.twocold.jrag.repository.SystemPromptRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Prompt 管理服务
 * 负责从数据库加载 Prompt 并提供内存缓存，支持动态刷新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptService {

    private final SystemPromptRepository systemPromptRepository;
    private final Map<String, String> promptCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshCache();
    }

    /**
     * 刷新 Prompt 缓存（从数据库重新加载）
     */
    public synchronized void refreshCache() {
        log.info("开始刷新 System Prompt 缓存...");
        Iterable<SystemPrompt> allPrompts = systemPromptRepository.findAll();
        Map<String, String> newCache = StreamSupport.stream(allPrompts.spliterator(), false)
                .collect(Collectors.toMap(SystemPrompt::getPromptKey, SystemPrompt::getPromptContent));
        
        promptCache.clear();
        promptCache.putAll(newCache);
        log.info("System Prompt 缓存刷新完成，共加载 {} 条 Prompt。", promptCache.size());
    }

    /**
     * 获取 Prompt 内容
     *
     * @param key Prompt Key
     * @param defaultValue 如果缓存中不存在时的默认值
     * @return Prompt 内容
     */
    public String getPrompt(String key, String defaultValue) {
        return promptCache.getOrDefault(key, defaultValue);
    }
    
    /**
     * 获取 Prompt 内容（如果在缓存中找不到，则返回 null）
     */
    public String getPrompt(String key) {
        return promptCache.get(key);
    }

    /**
     * 获取所有 Prompt
     */
    public Map<String, String> getAllPrompts() {
        return Map.copyOf(promptCache);
    }

    /**
     * 更新 Prompt
     */
    @Transactional
    public void updatePrompt(String key, String content, String description) {
        SystemPrompt prompt = systemPromptRepository.findById(key)
                .orElse(SystemPrompt.builder()
                        .promptKey(key)
                        .createdAt(LocalDateTime.now())
                        .build());
        
        prompt.setPromptContent(content);
        if (description != null) {
            prompt.setDescription(description);
        }
        prompt.setUpdatedAt(LocalDateTime.now());
        
        systemPromptRepository.save(prompt);
        
        // 更新缓存
        promptCache.put(key, content);
        log.info("已更新 Prompt: {}", key);
    }
}
