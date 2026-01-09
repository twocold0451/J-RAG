package com.twocold.jrag.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Agent 上下文容器
 * 使用 ThreadLocal 在 Agent 的执行线程中传递当前会话关联的文档 ID。
 * <p>
 * 作用：让 Agent 在调用 `searchKnowledgeBase` 工具时，知道应该在哪些文档范围内进行检索。
 */
public class AgentContext {
    private static final ThreadLocal<List<UUID>> DOCUMENT_IDS = new ThreadLocal<>();

    /**
     * 设置当前上下文的文档 ID 列表
     */
    public static void setDocumentIds(List<UUID> documentIds) {
        DOCUMENT_IDS.set(documentIds != null ? documentIds : new ArrayList<>());
    }

    /**
     * 获取当前上下文的文档 ID 列表
     */
    public static List<UUID> getDocumentIds() {
        List<UUID> ids = DOCUMENT_IDS.get();
        return ids != null ? ids : new ArrayList<>();
    }

    /**
     * 清理 ThreadLocal，防止内存泄漏
     */
    public static void clear() {
        DOCUMENT_IDS.remove();
    }
}