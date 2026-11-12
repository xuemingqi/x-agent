-- Run explicitly against vectordb. This schema is independent of the existing MySQL schema.
-- The database role must be allowed to install vector, or a database administrator must install it first.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge_base (
    owner_id varchar(128) NOT NULL,
    id varchar(160) NOT NULL,
    name varchar(100) NOT NULL,
    description varchar(500) NOT NULL DEFAULT '',
    is_default boolean NOT NULL DEFAULT FALSE,
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (owner_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS knowledge_base_default_idx ON knowledge_base (owner_id) WHERE is_default;
COMMENT ON TABLE knowledge_base IS '用户私有知识库目录；每位用户的公共库自动参与其全部对话检索';
COMMENT ON COLUMN knowledge_base.is_default IS '用户个人公共库标记，不代表跨用户共享；公共库不可删除';
COMMENT ON COLUMN knowledge_base.owner_id IS '知识库所属用户 ID，限定目录和文档访问范围';
COMMENT ON COLUMN knowledge_base.id IS '用户内唯一的知识库 ID，供文档与会话关联';
COMMENT ON COLUMN knowledge_base.name IS '用户设置的知识库名称，最长 100 字符';
COMMENT ON COLUMN knowledge_base.description IS '知识库内容说明，最长 500 字符';
COMMENT ON COLUMN knowledge_base.create_time IS '数据库记录的知识库创建时间';
COMMENT ON COLUMN knowledge_base.update_time IS '数据库记录的知识库最后修改时间';

CREATE TABLE IF NOT EXISTS knowledge_document (
    owner_id varchar(128) NOT NULL,
    knowledge_base_id varchar(160) NOT NULL,
    path varchar(1024) NOT NULL,
    name varchar(255) NOT NULL,
    content_type varchar(128) NOT NULL,
    source bytea NOT NULL,
    size bigint NOT NULL CHECK (size > 0),
    content text NOT NULL DEFAULT '',
    status varchar(16) NOT NULL CHECK (status IN ('INDEXING', 'READY', 'FAILED')),
    chunk_count integer NOT NULL DEFAULT 0,
    embedding_model varchar(255) NOT NULL DEFAULT '',
    generation varchar(36) NOT NULL,
    error_message varchar(500),
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (owner_id, knowledge_base_id, path),
    FOREIGN KEY (owner_id, knowledge_base_id) REFERENCES knowledge_base(owner_id, id) ON DELETE CASCADE
);
COMMENT ON COLUMN knowledge_document.knowledge_base_id IS '文档所属用户私有知识库 ID，同名文档可分别存在于不同知识库';
COMMENT ON TABLE knowledge_document IS 'RAG 文档、原文与索引状态；owner_id 隔离用户文档';
COMMENT ON COLUMN knowledge_document.generation IS '发布切片时校验的索引版本，阻止过期任务覆盖新上传或删除';
COMMENT ON COLUMN knowledge_document.source IS '原始文件内容，下载不再依赖共享磁盘';

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    owner_id varchar(128) NOT NULL,
    knowledge_base_id varchar(160) NOT NULL,
    document_path varchar(1024) NOT NULL,
    chunk_index integer NOT NULL CHECK (chunk_index > 0),
    content text NOT NULL,
    character_count integer NOT NULL,
    embedding_model varchar(255) NOT NULL,
    embedding_dimensions integer NOT NULL,
    embedding vector NOT NULL,
    PRIMARY KEY (owner_id, knowledge_base_id, document_path, chunk_index),
    FOREIGN KEY (owner_id, knowledge_base_id, document_path)
        REFERENCES knowledge_document(owner_id, knowledge_base_id, path) ON DELETE CASCADE,
    CHECK (vector_dims(embedding) = embedding_dimensions)
);
COMMENT ON COLUMN knowledge_chunk.knowledge_base_id IS '切片所属知识库 ID，与文档所有者和路径共同构成关联边界';
COMMENT ON TABLE knowledge_chunk IS 'RAG 切片与 pgvector 向量；仅 READY 文档可参与检索';
CREATE INDEX IF NOT EXISTS knowledge_chunk_model_idx
    ON knowledge_chunk (owner_id, knowledge_base_id, embedding_model, embedding_dimensions);
-- Exact cosine search works for every configured dimension. Add a model-specific HNSW expression index
-- only after measuring corpus size and choosing one fixed model/dimension for that index.
