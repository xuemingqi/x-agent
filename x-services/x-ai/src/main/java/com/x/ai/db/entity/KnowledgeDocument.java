package com.x.ai.db.entity;

import com.x.ai.db.enums.KnowledgeDocumentStatus;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * PostgreSQL 知识文档持久化快照。
 */
@Data
@Accessors(chain = true)
public class KnowledgeDocument {

    /**
     * 文档所有者，公共文档使用平台保留标识。
     */
    private String ownerId;

    /**
     * 同一所有者下唯一的文档相对路径。
     */
    private String path;

    /**
     * 文档名称。
     */
    private String name;

    /**
     * 原文内容类型。
     */
    private String contentType;

    /**
     * 原始文件字节，仅在下载或索引时读取。
     */
    private byte[] source;

    /**
     * 原文字节数。
     */
    private long size;

    /**
     * 提取的纯文本正文。
     */
    private String content;

    /**
     * 索引状态。
     */
    private KnowledgeDocumentStatus status;

    /**
     * 已发布切片数量。
     */
    private int chunkCount;

    /**
     * 索引使用的模型。
     */
    private String embeddingModel;

    /**
     * 此次索引版本，防止并发覆盖或删除后回写。
     */
    private String generation;

    /**
     * 可向用户展示的失败原因。
     */
    private String errorMessage;

    /**
     * 数据库维护的更新时间。
     */
    private java.time.LocalDateTime updateTime;
}
