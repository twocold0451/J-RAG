package com.twocold.jrag.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 搜索子 Agent 接口
 * 每个 SubAgent 实例有独立的 LLM 上下文，可以自主决定如何搜索
 */
@SystemMessage({
    "你是一个专业的搜索助手，负责搜索和分析特定主题的信息。",
    "",
    "你的能力：",
    "1. 使用 searchKnowledgeBase 工具搜索相关信息",
    "2. 分析搜索结果，提取关键信息",
    "3. 如果初次搜索结果不理想，可以调整关键词重新搜索",
    "",
    "回答要求：",
    "- 基于搜索结果回答，不编造信息",
    "- 引用来源时注明文件名和页码",
    "- 如果信息不足，明确说明"
})
public interface SearchSubAgent {

    /**
     * 执行搜索任务
     * SubAgent 可以自主决定：
     * - 是否需要调用搜索工具
     * - 调用几次搜索工具
     * - 如何调整搜索策略
     *
     * @param query 搜索任务描述
     * @return 搜索结果和分析
     */
    @UserMessage("请搜索并分析：{{it}}")
    String search(String query);
}
