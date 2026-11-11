-- Run explicitly against vectordb. This schema is independent of the existing MySQL schema.
-- The database role must be allowed to install vector, or a database administrator must install it first.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge_document (
    owner_id varchar(128) NOT NULL,
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
    PRIMARY KEY (owner_id, path)
);
COMMENT ON TABLE knowledge_document IS 'RAG 文档、原文与索引状态；owner_id 隔离个人和公共文档';
COMMENT ON COLUMN knowledge_document.generation IS '发布切片时校验的索引版本，阻止过期任务覆盖新上传或删除';
COMMENT ON COLUMN knowledge_document.source IS '原始文件内容，下载不再依赖共享磁盘';

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    owner_id varchar(128) NOT NULL,
    document_path varchar(1024) NOT NULL,
    chunk_index integer NOT NULL CHECK (chunk_index > 0),
    content text NOT NULL,
    character_count integer NOT NULL,
    embedding_model varchar(255) NOT NULL,
    embedding_dimensions integer NOT NULL,
    embedding vector NOT NULL,
    PRIMARY KEY (owner_id, document_path, chunk_index),
    FOREIGN KEY (owner_id, document_path) REFERENCES knowledge_document(owner_id, path) ON DELETE CASCADE,
    CHECK (vector_dims(embedding) = embedding_dimensions)
);
COMMENT ON TABLE knowledge_chunk IS 'RAG 切片与 pgvector 向量；仅 READY 文档可参与检索';
CREATE INDEX IF NOT EXISTS knowledge_chunk_model_idx
    ON knowledge_chunk (owner_id, embedding_model, embedding_dimensions);
-- Exact cosine search works for every configured dimension. Add a model-specific HNSW expression index
-- only after measuring corpus size and choosing one fixed model/dimension for that index.
