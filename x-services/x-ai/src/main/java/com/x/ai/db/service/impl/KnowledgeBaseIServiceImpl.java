package com.x.ai.db.service.impl;

import com.x.ai.config.KnowledgeVectorDatabase;
import com.x.ai.db.entity.KnowledgeBase;
import com.x.ai.db.service.KnowledgeBaseIService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseIServiceImpl implements KnowledgeBaseIService {

    private final KnowledgeVectorDatabase database;

    @Override
    public void ensureDefault(String ownerId) {
        database.jdbc().update("""
                INSERT INTO knowledge_base (owner_id, id, name, description, is_default)
                VALUES (?, ?, '公共知识库', '所有对话自动检索的个人公共资料', TRUE)
                ON CONFLICT DO NOTHING
                """, ownerId, "default-" + ownerId);
    }

    @Override
    public List<KnowledgeBase> list(String ownerId) {
        return database.jdbc().query("""
                SELECT owner_id, id, name, description, is_default FROM knowledge_base
                WHERE owner_id = ? ORDER BY is_default DESC, create_time, id
                """, (row, index) -> map(row), ownerId);
    }

    @Override
    public List<KnowledgeBase> findOwned(String ownerId, List<String> ids) {
        if (ids.isEmpty()) return List.of();
        return new NamedParameterJdbcTemplate(database.jdbc()).query("""
                SELECT owner_id, id, name, description, is_default FROM knowledge_base
                WHERE owner_id = :ownerId AND id IN (:ids) ORDER BY is_default DESC, create_time, id
                """, new MapSqlParameterSource("ownerId", ownerId).addValue("ids", ids), (row, index) -> map(row));
    }

    @Override
    public KnowledgeBase create(KnowledgeBase knowledgeBase) {
        return database.jdbc().queryForObject("""
                INSERT INTO knowledge_base (owner_id, id, name, description, is_default)
                VALUES (?, ?, ?, ?, FALSE) RETURNING *
                """, (row, index) -> map(row), knowledgeBase.getOwnerId(), knowledgeBase.getId(),
                knowledgeBase.getName(), knowledgeBase.getDescription());
    }

    @Override
    public KnowledgeBase update(String ownerId, String id, String name, String description) {
        List<KnowledgeBase> updated = database.jdbc().query("""
                UPDATE knowledge_base SET name = ?, description = ?, update_time = CURRENT_TIMESTAMP
                WHERE owner_id = ? AND id = ? RETURNING *
                """, (row, index) -> map(row), name, description, ownerId, id);
        return updated.isEmpty() ? null : updated.getFirst();
    }

    @Override
    public boolean delete(String ownerId, String id) {
        return database.jdbc().update("DELETE FROM knowledge_base WHERE owner_id = ? AND id = ? AND NOT is_default",
                ownerId, id) > 0;
    }

    /**
     * 映射不含文档内容的目录快照。
     */
    private KnowledgeBase map(ResultSet row) throws SQLException {
        return new KnowledgeBase().setOwnerId(row.getString("owner_id")).setId(row.getString("id"))
                .setName(row.getString("name")).setDescription(row.getString("description"))
                .setDefault(row.getBoolean("is_default"));
    }
}
