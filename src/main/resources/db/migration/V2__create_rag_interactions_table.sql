-- =================================================================================================
-- V2__create_rag_interactions_table.sql
-- 创建 RAG 交互记录表，用于存储完整的问答上下文，便于后续的 RAG 评估 (Ragas) 和审计
-- =================================================================================================

SET search_path TO jrag_core, public;

CREATE TABLE rag_interactions (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_query TEXT NOT NULL,
    rewritten_query TEXT,
    ai_response TEXT,
    retrieved_contexts JSONB, -- 存储检索到的文档片段详情 (包含 content, score, meta)
    created_at TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE rag_interactions IS 'RAG 交互完整记录表 (用于评估与审计)';
COMMENT ON COLUMN rag_interactions.trace_id IS '全链路追踪 ID';
COMMENT ON COLUMN rag_interactions.user_query IS '用户原始问题';
COMMENT ON COLUMN rag_interactions.rewritten_query IS '重写后的查询词';
COMMENT ON COLUMN rag_interactions.retrieved_contexts IS '检索到的上下文片段 (JSON)';

CREATE INDEX idx_rag_interactions_trace_id ON rag_interactions(trace_id);
CREATE INDEX idx_rag_interactions_conversation_id ON rag_interactions(conversation_id);
CREATE INDEX idx_rag_interactions_created_at ON rag_interactions(created_at);
