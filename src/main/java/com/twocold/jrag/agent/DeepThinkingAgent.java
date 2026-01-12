package com.twocold.jrag.agent;

import com.twocold.jrag.config.Observed;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 深度思考 Agent 接口
 * 基于 ReAct (Reasoning + Acting) 范式实现的智能体。
 * 能够自主决定是否调用工具（如搜索知识库、分解查询）来解决复杂问题。
 */
public interface DeepThinkingAgent {

    /**
     * 执行 Agent 对话
     * 系统提示词通过动态注入实现数据库管理。
     *
     * @param messages 对话历史消息列表
     * @return Agent 的最终回答
     */
    @Observed(name = "Deep Thinking Agent")
    String chat(List<ChatMessage> messages);
}