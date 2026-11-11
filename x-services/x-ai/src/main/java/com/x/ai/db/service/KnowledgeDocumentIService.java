package com.x.ai.db.service;

import com.x.ai.common.domain.KnowledgeChunkPageDo;
import com.x.ai.common.domain.KnowledgeSearchResultDo;
import com.x.ai.db.entity.KnowledgeDocument;

import java.util.List;

/**
 * 独立 PostgreSQL 文档与向量持久化边界，不参与现有 MySQL MyBatis 会话。
 */
public interface KnowledgeDocumentIService {

    /**
     * 查询所有者文档元数据，不读取原文二进制。
     */
    List<KnowledgeDocument> list(String ownerId);

    /**
     * 按所有者和路径读取文档，未找到时返回 null。
     */
    KnowledgeDocument find(String ownerId, String path, boolean includeSource);

    /**
     * 原子替换原文、使旧索引失效并开始新版本。
     */
    KnowledgeDocument stage(String ownerId, String path, String name, String contentType, byte[] source);

    /**
     * 基于数据库当前原文原子开始重建，返回当前版本快照。
     */
    KnowledgeDocument restart(String ownerId, String path);

    /**
     * 按版本保存提取正文，向量服务不可用时仍可预览。
     */
    boolean saveContent(KnowledgeDocument document, String content);

    /**
     * 仅当前版本可发布切片和 READY 状态，删除或覆盖后的旧任务返回 false。
     */
    boolean publish(KnowledgeDocument document, String content, List<String> chunks,
                    List<List<Double>> embeddings, String model, int dimensions);

    /**
     * 仅当前版本可登记失败，不覆盖后续上传或重建的状态。
     */
    void fail(KnowledgeDocument document, String message);

    /**
     * 删除所有者文档，切片由外键级联删除。
     */
    boolean delete(String ownerId, String path);

    /**
     * 读取指定所有者文档的切片分页。
     */
    KnowledgeChunkPageDo chunks(String ownerId, String path, int page, int size);

    /**
     * 在明确所有者集合内使用同一模型和维度进行 pgvector 余弦检索。
     */
    List<KnowledgeSearchResultDo> search(List<String> owners, List<Double> embedding, String model,
                                        int dimensions, double minimumScore, int limit);
}
