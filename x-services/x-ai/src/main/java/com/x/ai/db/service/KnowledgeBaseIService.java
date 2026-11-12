package com.x.ai.db.service;

import com.x.ai.db.entity.KnowledgeBase;

import java.util.List;

/**
 * PostgreSQL 知识库目录存储，所有查询均带所有者条件。
 */
public interface KnowledgeBaseIService {

    /**
     * 原子确保该用户的公共知识库存在。
     */
    void ensureDefault(String ownerId);

    /**
     * 查询该用户全部知识库，公共库优先。
     */
    List<KnowledgeBase> list(String ownerId);

    /**
     * 仅查询该用户拥有的指定知识库。
     */
    List<KnowledgeBase> findOwned(String ownerId, List<String> ids);

    /**
     * 新建独立知识库并返回持久化结果。
     */
    KnowledgeBase create(KnowledgeBase knowledgeBase);

    /**
     * 修改该用户的知识库说明与名称；不存在时返回 null。
     */
    KnowledgeBase update(String ownerId, String id, String name, String description);

    /**
     * 删除该用户非公共知识库，文档和切片由外键级联清理。
     */
    boolean delete(String ownerId, String id);
}
