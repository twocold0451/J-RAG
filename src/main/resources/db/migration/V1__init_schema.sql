-- =================================================================================================
-- V1__init_schema.sql
-- J-RAG 初始数据库结构
-- 引入 Schema 隔离: 所有表都将创建在 'jrag_core' 模式下。
-- =================================================================================================

-- 0. Schema 和 扩展设置
CREATE SCHEMA IF NOT EXISTS jrag_core;

-- 将 vector 扩展安装在 public 模式下，以便所有模式都可以访问
CREATE EXTENSION IF NOT EXISTS vector SCHEMA public;

-- 设置搜索路径：优先查找 jrag_core 中的表，其次查找 public 中的扩展/函数
SET search_path TO jrag_core, public;

-- 2. 用户表 (Users)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100),
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE users IS '系统用户表';
COMMENT ON COLUMN users.id IS '主键ID';
COMMENT ON COLUMN users.username IS '用户名 (唯一)';
COMMENT ON COLUMN users.email IS '电子邮箱';
COMMENT ON COLUMN users.password_hash IS '加密后的密码哈希';
COMMENT ON COLUMN users.salt IS '密码加密使用的盐值';
COMMENT ON COLUMN users.role IS '用户角色 (ADMIN, USER)';
COMMENT ON COLUMN users.created_at IS '注册时间';

-- 3. 文档表 (Documents)
CREATE TABLE documents (
    id UUID PRIMARY KEY,
    name TEXT,
    user_id BIGINT,
    status VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    progress INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    category VARCHAR(50),
    file_size BIGINT,
    uploaded_at TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE documents IS '上传文档的元数据表';
COMMENT ON COLUMN documents.id IS '文档唯一标识 (UUID)';
COMMENT ON COLUMN documents.name IS '原始文件名';
COMMENT ON COLUMN documents.user_id IS '上传用户ID';
COMMENT ON COLUMN documents.status IS '处理状态 (PENDING, PROCESSING, COMPLETED, FAILED)';
COMMENT ON COLUMN documents.progress IS '处理进度百分比 (0-100)';
COMMENT ON COLUMN documents.error_message IS '处理失败时的错误信息';
COMMENT ON COLUMN documents.is_public IS '是否为公开文档';
COMMENT ON COLUMN documents.category IS '文档分类';
COMMENT ON COLUMN documents.file_size IS '文件大小 (字节)';
COMMENT ON COLUMN documents.uploaded_at IS '上传时间';

-- 4. 向量分片表 (Chunks)
CREATE TABLE chunks (
    id UUID PRIMARY KEY,
    document_id UUID,
    content TEXT,
    content_vector VECTOR(1024), -- 假设使用 1024 维度的 Embedding 模型 (如 BGE-M3)
    chunk_index INT,
    source_meta JSONB,
    chunker_name VARCHAR(255),
    content_keywords TEXT, 
    content_search tsvector GENERATED ALWAYS AS (to_tsvector('simple', coalesce(content_keywords, ''))) STORED,
    created_at TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE chunks IS '文档切片及其向量数据表';
COMMENT ON COLUMN chunks.document_id IS '所属文档ID';
COMMENT ON COLUMN chunks.content IS '切片文本内容';
COMMENT ON COLUMN chunks.content_vector IS '文本内容的 Embedding 向量';
COMMENT ON COLUMN chunks.chunk_index IS '切片在文档中的顺序索引 (从0开始)';
COMMENT ON COLUMN chunks.source_meta IS '元数据 (如页码、元素类型等 JSON)';
COMMENT ON COLUMN chunks.chunker_name IS '使用的切分器名称';
COMMENT ON COLUMN chunks.content_keywords IS '用于全文检索的分词关键词';
COMMENT ON COLUMN chunks.content_search IS '自动生成的全文检索向量 (tsvector)';

-- 索引：向量相似度搜索 (IVF Flat)
CREATE INDEX ON chunks USING ivfflat (content_vector vector_cosine_ops) WITH (lists = 100);
-- 索引：全文关键词搜索 (GIN)
CREATE INDEX idx_chunks_content_search ON chunks USING GIN(content_search);

-- 5. 用户组表 (User Groups)
CREATE TABLE user_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE user_groups IS '用户分组表 (部门/团队)';
COMMENT ON COLUMN user_groups.name IS '组名称';
COMMENT ON COLUMN user_groups.description IS '组描述';

-- 插入默认用户组
INSERT INTO user_groups (name, description) VALUES
    ('销售组', '销售部门用户组'),
    ('技术组', '技术部门用户组'),
    ('人事组', '人事部门用户组'),
    ('财务组', '财务部门用户组'),
    ('法务组', '法务部门用户组');

-- 6. 用户组成员表 (User Group Members)
CREATE TABLE user_group_members (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(group_id, user_id)
);

COMMENT ON TABLE user_group_members IS '用户与用户组的关联表';
CREATE INDEX idx_user_group_members_group_id ON user_group_members(group_id);
CREATE INDEX idx_user_group_members_user_id ON user_group_members(user_id);

-- 7. 对话模板表 (Templates)
CREATE TABLE templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    icon VARCHAR(10),
    user_id BIGINT NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    visible_groups TEXT, -- JSON 数组字符串，例如 "[1, 2]"
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE templates IS '对话模板 (场景预设) 表';
COMMENT ON COLUMN templates.name IS '模板名称';
COMMENT ON COLUMN templates.description IS '模板描述/提示词设定';
COMMENT ON COLUMN templates.icon IS '模板图标 (Emoji)';
COMMENT ON COLUMN templates.user_id IS '创建者ID';
COMMENT ON COLUMN templates.is_public IS '是否全员可见';
COMMENT ON COLUMN templates.visible_groups IS '可见的用户组ID列表 (JSON数组)';

CREATE INDEX idx_templates_user_id ON templates(user_id);
CREATE INDEX idx_templates_is_public ON templates(is_public);

-- 8. 模板关联文档表 (Template Documents)
CREATE TABLE template_documents (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL,
    document_id UUID NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(template_id, document_id)
);

COMMENT ON TABLE template_documents IS '对话模板与知识库文档的关联表';
CREATE INDEX idx_template_documents_template_id ON template_documents(template_id);

-- 9. 会话表 (Conversations)
CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    parent_id BIGINT, 
    allowed_users TEXT, -- 逗号分隔的用户ID字符串
    template_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE conversations IS '用户聊天会话表';
COMMENT ON COLUMN conversations.user_id IS '所属用户ID';
COMMENT ON COLUMN conversations.title IS '会话标题';
COMMENT ON COLUMN conversations.is_public IS '是否公开分享';
COMMENT ON COLUMN conversations.parent_id IS '父会话ID (用于Fork)';
COMMENT ON COLUMN conversations.allowed_users IS '允许访问该私有会话的用户ID列表';
COMMENT ON COLUMN conversations.template_id IS '使用的对话模板ID';

CREATE INDEX idx_conversations_user_id ON conversations (user_id);
CREATE INDEX idx_conversations_parent_id ON conversations (parent_id);

-- 10. 聊天消息表 (Chat Messages)
CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(50) NOT NULL, -- USER, ASSISTANT, SYSTEM
    content TEXT NOT NULL,
    sources TEXT, 
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE chat_messages IS '会话消息记录表';
COMMENT ON COLUMN chat_messages.role IS '消息角色 (USER=用户, ASSISTANT=AI, SYSTEM=系统)';
COMMENT ON COLUMN chat_messages.content IS '消息内容';
COMMENT ON COLUMN chat_messages.sources IS '引用的来源片段 (JSON字符串)';

CREATE INDEX idx_chat_messages_conversation_id_created_at ON chat_messages (conversation_id, created_at);

-- 11. 会话临时文档表 (Conversation Documents)
-- 用户可以在特定会话中临时上传文档，而不通过模板
CREATE TABLE conversation_documents (
    conversation_id BIGINT NOT NULL,
    document_id UUID NOT NULL,
    PRIMARY KEY (conversation_id, document_id)
);

COMMENT ON TABLE conversation_documents IS '会话与临时文档的关联表';

-- 12. 初始化管理员账号
-- 用户名: admin
-- 密码: XHy@azy5Mhy2
INSERT INTO users (username, email, password_hash, salt, role, created_at)
VALUES (
    'admin',
    'admin@example.com',
    'R+Clvd5QWm2I84TOpvEO4wLznJDTcYFXJkM061fvhbU=',
    'HHUpaFSxLlLhCPmZqSs0bA==',
    'ADMIN', 
    NOW()
)
ON CONFLICT (username) DO NOTHING;