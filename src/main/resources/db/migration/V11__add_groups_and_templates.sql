-- 1. Create user_groups table
CREATE TABLE user_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Insert default groups
INSERT INTO user_groups (name, description) VALUES
    ('销售组', '销售部门用户组'),
    ('技术组', '技术部门用户组'),
    ('人事组', '人事部门用户组'),
    ('财务组', '财务部门用户组'),
    ('法务组', '法务部门用户组');

-- 2. Create user_group_members table
CREATE TABLE user_group_members (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(group_id, user_id)
);

CREATE INDEX idx_user_group_members_group_id ON user_group_members(group_id);
CREATE INDEX idx_user_group_members_user_id ON user_group_members(user_id);

-- 3. Create templates table
CREATE TABLE templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    icon VARCHAR(10),
    user_id BIGINT NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    visible_groups TEXT, -- JSON Array of Group IDs
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_templates_user_id ON templates(user_id);
CREATE INDEX idx_templates_is_public ON templates(is_public);

-- 4. Create template_documents table
CREATE TABLE template_documents (
    id BIGSERIAL PRIMARY KEY,
    template_id BIGINT NOT NULL,
    document_id UUID NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(template_id, document_id)
);

CREATE INDEX idx_template_documents_template_id ON template_documents(template_id);

-- 5. Alter documents table
ALTER TABLE documents ADD COLUMN category VARCHAR(50);
ALTER TABLE documents ADD COLUMN file_size BIGINT;

-- 6. Alter conversations table
ALTER TABLE conversations ADD COLUMN template_id BIGINT;
