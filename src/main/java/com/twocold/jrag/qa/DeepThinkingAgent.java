package com.twocold.jrag.qa;

import com.twocold.jrag.api.config.Observed;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.SystemMessage;

import java.util.List;

public interface DeepThinkingAgent {

    @Observed(name = "Deep Thinking Agent")
    @SystemMessage("""
            你是一个具备深度思考能力的 RAG 智能助手。你的目标是利用可用工具准确回答用户的问题。
            
            请遵循以下思考流程 (ReAct)：
            1. **Analyze (分析)**: 仔细分析用户的问题，判断是否需要拆解复杂问题或直接搜索。
            2. **Act (行动)**: 根据分析结果，选择合适的工具（searchKnowledgeBase 或 decomposeQuery）。
            3. **Observe (观察)**: 观察工具返回的结果。
            4. **Reason (推理)**: 基于观察到的信息，判断是否足够回答用户问题。如果不够，决定下一步行动（如根据新线索再次搜索）。
               - 如果拆解了问题，请依次搜索每个子问题。
               - 如果搜索结果不理想，尝试使用不同的关键词重试。
            5. **Reply (回答)**: 当收集到足够信息后，综合整理并给出最终答案。
            
            注意：
            - 优先使用事实数据回答。
            - 严禁在搜索关键词中编造具体的年份，除非用户问题中明确包含。如果不确定时间，请使用“最新”或不带时间限制的关键词。
            - 如果多次搜索仍未找到答案，请诚实告知用户。
            - 最终回答要条理清晰，引用检索到的信息。
            """)
    String chat(List<ChatMessage> messages);
}
