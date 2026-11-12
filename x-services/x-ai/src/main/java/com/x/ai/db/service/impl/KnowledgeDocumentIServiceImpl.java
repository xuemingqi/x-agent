package com.x.ai.db.service.impl;

import com.x.ai.common.domain.KnowledgeChunkDo;
import com.x.ai.common.domain.KnowledgeChunkPageDo;
import com.x.ai.common.domain.KnowledgeSearchResultDo;
import com.x.ai.config.KnowledgeVectorDatabase;
import com.x.ai.db.entity.KnowledgeDocument;
import com.x.ai.db.enums.KnowledgeDocumentStatus;
import com.x.ai.db.service.KnowledgeDocumentIService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeDocumentIServiceImpl implements KnowledgeDocumentIService {

    private static final String METADATA_COLUMNS = "owner_id, knowledge_base_id, path, name, content_type, size, "
            + "status, chunk_count, embedding_model, generation, error_message, update_time";

    private final KnowledgeVectorDatabase database;

    @Override
    public List<KnowledgeDocument> list(String ownerId, String knowledgeBaseId) {
        return database.jdbc().query("SELECT " + METADATA_COLUMNS
                + " FROM knowledge_document WHERE owner_id = ? AND knowledge_base_id = ? ORDER BY path",
                (row, index) -> metadata(row), ownerId, knowledgeBaseId);
    }

    @Override
    public KnowledgeDocument find(String ownerId, String knowledgeBaseId, String path, boolean includeSource) {
        List<KnowledgeDocument> documents = database.jdbc().query("SELECT " + METADATA_COLUMNS
                        + ", content" + (includeSource ? ", source" : "")
                        + " FROM knowledge_document WHERE owner_id = ? AND knowledge_base_id = ? AND path = ?",
                (row, index) -> metadata(row).setContent(row.getString("content"))
                        .setSource(includeSource ? row.getBytes("source") : null), ownerId, knowledgeBaseId, path);
        return documents.isEmpty() ? null : documents.getFirst();
    }

    @Override
    public KnowledgeDocument stage(String ownerId, String knowledgeBaseId, String path,
                                   String name, String contentType, byte[] source) {
        String generation = UUID.randomUUID().toString();
        return database.transaction().execute(status -> {
            KnowledgeDocument document = database.jdbc().queryForObject("""
                    INSERT INTO knowledge_document
                        (owner_id, knowledge_base_id, path, name, content_type, source, size, status, generation)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'INDEXING', ?)
                    ON CONFLICT (owner_id, knowledge_base_id, path) DO UPDATE SET
                        name = EXCLUDED.name, content_type = EXCLUDED.content_type,
                        source = EXCLUDED.source, size = EXCLUDED.size, content = '', status = 'INDEXING',
                        chunk_count = 0, embedding_model = '', generation = EXCLUDED.generation,
                        error_message = NULL, update_time = CURRENT_TIMESTAMP
                    RETURNING *
                    """, (row, index) -> metadata(row).setSource(row.getBytes("source")),
                    ownerId, knowledgeBaseId, path, name, contentType, source, source.length, generation);
            database.jdbc().update("""
                    DELETE FROM knowledge_chunk WHERE owner_id = ? AND knowledge_base_id = ? AND document_path = ?
                    """, ownerId, knowledgeBaseId, path);
            return document;
        });
    }

    @Override
    public KnowledgeDocument restart(String ownerId, String knowledgeBaseId, String path) {
        String generation = UUID.randomUUID().toString();
        return database.transaction().execute(status -> {
            List<KnowledgeDocument> documents = database.jdbc().query("""
                    UPDATE knowledge_document SET status = 'INDEXING', chunk_count = 0,
                        generation = ?, error_message = NULL, update_time = CURRENT_TIMESTAMP
                    WHERE owner_id = ? AND knowledge_base_id = ? AND path = ? RETURNING *
                    """, (row, index) -> metadata(row).setSource(row.getBytes("source")),
                    generation, ownerId, knowledgeBaseId, path);
            if (documents.isEmpty()) return null;
            database.jdbc().update("""
                    DELETE FROM knowledge_chunk WHERE owner_id = ? AND knowledge_base_id = ? AND document_path = ?
                    """, ownerId, knowledgeBaseId, path);
            return documents.getFirst();
        });
    }

    @Override
    public boolean saveContent(KnowledgeDocument document, String content) {
        return database.jdbc().update("""
                UPDATE knowledge_document SET content = ?
                WHERE owner_id = ? AND knowledge_base_id = ? AND path = ? AND generation = ?
                """, content, document.getOwnerId(), document.getKnowledgeBaseId(),
                document.getPath(), document.getGeneration()) > 0;
    }

    @Override
    public boolean publish(KnowledgeDocument document, String content, List<String> chunks,
                           List<List<Double>> embeddings, String model, int dimensions) {
        Boolean published = database.transaction().execute(status -> {
            // 行锁和版本检查覆盖完整发布事务，旧任务不得把已删除或替换的索引写回来。
            List<String> versions = database.jdbc().queryForList("""
                    SELECT generation FROM knowledge_document
                    WHERE owner_id = ? AND knowledge_base_id = ? AND path = ? AND generation = ? FOR UPDATE
                    """, String.class, document.getOwnerId(), document.getKnowledgeBaseId(),
                    document.getPath(), document.getGeneration());
            if (versions.isEmpty()) return false;
            JdbcTemplate jdbc = database.jdbc();
            jdbc.update("DELETE FROM knowledge_chunk WHERE owner_id = ? AND knowledge_base_id = ? AND document_path = ?",
                    document.getOwnerId(), document.getKnowledgeBaseId(), document.getPath());
            List<Object[]> rows = new ArrayList<>(chunks.size());
            for (int index = 0; index < chunks.size(); index++) {
                String text = chunks.get(index);
                rows.add(new Object[]{document.getOwnerId(), document.getKnowledgeBaseId(), document.getPath(),
                        index + 1, text,
                        text.codePointCount(0, text.length()), model, dimensions, vectorLiteral(embeddings.get(index))});
            }
            jdbc.batchUpdate("""
                    INSERT INTO knowledge_chunk (owner_id, knowledge_base_id, document_path, chunk_index, content,
                        character_count, embedding_model, embedding_dimensions, embedding)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector))
                    """, rows);
            jdbc.update("""
                    UPDATE knowledge_document SET content = ?, status = 'READY', chunk_count = ?,
                        embedding_model = ?, error_message = NULL, update_time = CURRENT_TIMESTAMP
                    WHERE owner_id = ? AND knowledge_base_id = ? AND path = ? AND generation = ?
                    """, content, chunks.size(), model, document.getOwnerId(), document.getKnowledgeBaseId(),
                    document.getPath(), document.getGeneration());
            return true;
        });
        return Boolean.TRUE.equals(published);
    }

    @Override
    public void fail(KnowledgeDocument document, String message) {
        database.jdbc().update("""
                UPDATE knowledge_document SET status = 'FAILED', error_message = ?, update_time = CURRENT_TIMESTAMP
                WHERE owner_id = ? AND knowledge_base_id = ? AND path = ? AND generation = ?
                """, message, document.getOwnerId(), document.getKnowledgeBaseId(),
                document.getPath(), document.getGeneration());
    }

    @Override
    public boolean delete(String ownerId, String knowledgeBaseId, String path) {
        return database.jdbc().update("""
                DELETE FROM knowledge_document WHERE owner_id = ? AND knowledge_base_id = ? AND path = ?
                """, ownerId, knowledgeBaseId, path) > 0;
    }

    @Override
    public KnowledgeChunkPageDo chunks(String ownerId, String knowledgeBaseId, String path, int page, int size) {
        List<KnowledgeChunkDo> items = database.jdbc().query("""
                SELECT chunk_index, content, character_count FROM knowledge_chunk
                WHERE owner_id = ? AND knowledge_base_id = ? AND document_path = ? ORDER BY chunk_index LIMIT ? OFFSET ?
                """, (row, index) -> new KnowledgeChunkDo().setIndex(row.getInt("chunk_index"))
                .setContent(row.getString("content")).setCharacterCount(row.getInt("character_count")),
                ownerId, knowledgeBaseId, path, size, (long) (page - 1) * size);
        Long total = database.jdbc().queryForObject("""
                SELECT COUNT(*) FROM knowledge_chunk WHERE owner_id = ? AND knowledge_base_id = ? AND document_path = ?
                """, Long.class, ownerId, knowledgeBaseId, path);
        return new KnowledgeChunkPageDo().setItems(items).setTotal(total == null ? 0 : total).setPage(page).setSize(size);
    }

    @Override
    public boolean hasSearchableChunks(String ownerId, List<String> knowledgeBaseIds, String model, int dimensions) {
        if (knowledgeBaseIds.isEmpty()) return false;
        Boolean exists = new NamedParameterJdbcTemplate(database.jdbc()).queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM knowledge_chunk c JOIN knowledge_document d
                        ON d.owner_id = c.owner_id AND d.knowledge_base_id = c.knowledge_base_id
                        AND d.path = c.document_path
                    WHERE c.owner_id = :ownerId AND c.knowledge_base_id IN (:knowledgeBaseIds)
                        AND d.status = 'READY' AND c.embedding_model = :model AND c.embedding_dimensions = :dimensions
                )
                """, new MapSqlParameterSource("ownerId", ownerId).addValue("knowledgeBaseIds", knowledgeBaseIds)
                .addValue("model", model).addValue("dimensions", dimensions), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public List<KnowledgeSearchResultDo> search(String ownerId, List<String> knowledgeBaseIds, List<Double> embedding,
                                               String model, int dimensions, double minimumScore, int limit) {
        if (knowledgeBaseIds.isEmpty()) return List.of();
        String sql = """
                WITH candidates AS MATERIALIZED (
                    SELECT d.knowledge_base_id, b.name AS knowledge_base_name, d.path, d.name,
                        c.chunk_index, c.content, c.embedding
                    FROM knowledge_chunk c JOIN knowledge_document d
                        ON d.owner_id = c.owner_id AND d.knowledge_base_id = c.knowledge_base_id
                        AND d.path = c.document_path
                    JOIN knowledge_base b ON b.owner_id = d.owner_id AND b.id = d.knowledge_base_id
                    WHERE c.owner_id = :ownerId AND c.knowledge_base_id IN (:knowledgeBaseIds)
                        AND d.status = 'READY' AND c.embedding_model = :model AND c.embedding_dimensions = :dimensions
                )
                SELECT knowledge_base_id, knowledge_base_name, path, name, chunk_index, content,
                    1 - (embedding <=> CAST(:vector AS vector)) AS score
                FROM candidates WHERE 1 - (embedding <=> CAST(:vector AS vector)) >= :minimumScore
                ORDER BY embedding <=> CAST(:vector AS vector), knowledge_base_id, path, chunk_index LIMIT :limit
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource("ownerId", ownerId)
                .addValue("knowledgeBaseIds", knowledgeBaseIds).addValue("model", model)
                .addValue("dimensions", dimensions).addValue("vector", vectorLiteral(embedding))
                .addValue("minimumScore", minimumScore).addValue("limit", limit);
        return new NamedParameterJdbcTemplate(database.jdbc()).query(sql, parameters,
                (row, index) -> new KnowledgeSearchResultDo()
                        .setKnowledgeBaseId(row.getString("knowledge_base_id"))
                        .setKnowledgeBaseName(row.getString("knowledge_base_name"))
                        .setPath(row.getString("path")).setName(row.getString("name"))
                        .setChunkIndex(row.getInt("chunk_index")).setContent(row.getString("content"))
                        .setScore(row.getDouble("score")));
    }

    /**
     * 仅供已校验的有限数值向量绑定到 JDBC 参数，绝不拼接为 SQL。
     */
    private String vectorLiteral(List<Double> vector) {
        return vector.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * 映射不含原文二进制的数据库元数据快照。
     */
    private KnowledgeDocument metadata(ResultSet row) throws SQLException {
        return new KnowledgeDocument().setKnowledgeBaseId(row.getString("knowledge_base_id"))
                .setOwnerId(row.getString("owner_id")).setPath(row.getString("path"))
                .setName(row.getString("name")).setContentType(row.getString("content_type"))
                .setSize(row.getLong("size")).setStatus(KnowledgeDocumentStatus.fromCode(row.getString("status")))
                .setChunkCount(row.getInt("chunk_count")).setEmbeddingModel(row.getString("embedding_model"))
                .setGeneration(row.getString("generation")).setErrorMessage(row.getString("error_message"))
                .setUpdateTime(row.getTimestamp("update_time").toLocalDateTime());
    }
}
