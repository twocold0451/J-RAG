CREATE TABLE conversations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL, -- Removed FK
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL, -- Removed FK
    role VARCHAR(50) NOT NULL, -- USER, ASSISTANT, SYSTEM
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE conversation_documents (
    conversation_id BIGINT NOT NULL, -- Removed FK
    document_id UUID NOT NULL, -- Removed FK
    PRIMARY KEY (conversation_id, document_id)
);

-- Add index to chat_messages for faster lookup by conversation_id and ordering
CREATE INDEX idx_chat_messages_conversation_id_created_at ON chat_messages (conversation_id, created_at);

-- Add index to conversations for faster lookup by user_id
CREATE INDEX idx_conversations_user_id ON conversations (user_id);
