package com.x.ai.service;

import com.x.ai.common.domain.KnowledgeChunkPageDo;
import com.x.ai.common.domain.KnowledgeDocumentDo;
import com.x.ai.common.domain.KnowledgeDownloadDo;
import com.x.ai.common.domain.KnowledgePreviewDo;
import com.x.ai.common.domain.KnowledgeSearchResultDo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户文档 RAG 流程，调用方从请求或工具上下文确定可信所有者。
 */
public interface RagKnowledgeService {

    /**
     * 查询指定所有者的文档及索引状态。
     */
    List<KnowledgeDocumentDo> list(String ownerId, String knowledgeBaseId);

    /**
     * 保存上传原文，同名替换后重新切片和索引；失败状态可重试。
     */
    KnowledgeDocumentDo upload(String ownerId, String knowledgeBaseId, MultipartFile file);

    /**
     * 保存指定相对路径的用户文档字节。
     */
    KnowledgeDocumentDo upload(String ownerId, String knowledgeBaseId, String path, byte[] source);

    /**
     * 从数据库读取指定所有者的原文供下载。
     */
    KnowledgeDownloadDo download(String ownerId, String knowledgeBaseId, String path);

    /**
     * 原子删除指定所有者的原文与全部切片。
     */
    boolean delete(String ownerId, String knowledgeBaseId, String path);

    /**
     * 读取可安全展示的提取纯文本，向量化失败时仍可预览。
     */
    KnowledgePreviewDo preview(String ownerId, String knowledgeBaseId, String path);

    /**
     * 分页读取文档切片，页码从 1 开始，每页最多 100 条。
     */
    KnowledgeChunkPageDo chunks(String ownerId, String knowledgeBaseId, String path, int page, int size);

    /**
     * 使用数据库当前原文重新切片和向量化。
     */
    KnowledgeDocumentDo reindex(String ownerId, String knowledgeBaseId, String path);

    /**
     * 仅检索指定用户的文档；每次最多 20 条。
     */
    List<KnowledgeSearchResultDo> search(String userId, List<String> knowledgeBaseIds, String query, int limit);

}
