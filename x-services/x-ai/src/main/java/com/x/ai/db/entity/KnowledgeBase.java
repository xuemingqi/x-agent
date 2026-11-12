package com.x.ai.db.entity;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * PostgreSQL 知识库目录持久化快照。
 */
@Data
@Accessors(chain = true)
public class KnowledgeBase {

    /**
     * 知识库 ID，在所属用户内唯一。
     */
    private String id;

    /**
     * 知识库所属用户 ID。
     */
    private String ownerId;

    /**
     * 用户设置的知识库名称。
     */
    private String name;

    /**
     * 知识库说明。
     */
    private String description;

    /**
     * 是否为该用户所有对话默认检索的公共知识库。
     */
    private boolean isDefault;
}
