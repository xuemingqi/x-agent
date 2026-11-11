package com.x.ai.service;

import com.x.ai.common.domain.KnowledgeChunkPageDo;
import com.x.ai.common.domain.KnowledgeDocumentDo;
import com.x.ai.common.domain.KnowledgeDownloadDo;
import com.x.ai.common.domain.KnowledgePreviewDo;
import com.x.ai.common.domain.KnowledgeSearchResultDo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 个人和公共文档共享的 RAG 流程，调用方负责确定可信所有者与公共管理权限。
 */
public interface RagKnowledgeService {

    /**
     * 查询指定所有者的文档及索引状态。
     */
    List<KnowledgeDocumentDo> list(String ownerId);

    /**
     * 保存上传原文，同名替换后重新切片和索引；失败状态可重试。
     */
    KnowledgeDocumentDo upload(String ownerId, MultipartFile file);

    /**
     * 保存指定相对路径的文档字节，供公共文本写入调用。
     */
    KnowledgeDocumentDo upload(String ownerId, String path, byte[] source);

    /**
     * 从数据库读取指定所有者的原文供下载。
     */
    KnowledgeDownloadDo download(String ownerId, String path);

    /**
     * 原子删除指定所有者的原文与全部切片。
     */
    boolean delete(String ownerId, String path);

    /**
     * 读取可安全展示的提取纯文本，向量化失败时仍可预览。
     */
    KnowledgePreviewDo preview(String ownerId, String path);

    /**
     * 分页读取文档切片，页码从 1 开始，每页最多 100 条。
     */
    KnowledgeChunkPageDo chunks(String ownerId, String path, int page, int size);

    /**
     * 使用数据库当前原文重新切片和向量化。
     */
    KnowledgeDocumentDo reindex(String ownerId, String path);

    /**
     * 检索用户个人文档，可选择合并公共文档；每次最多 20 条。
     */
    List<KnowledgeSearchResultDo> search(String userId, String query, int limit, boolean includePublic);

    /**
     * 仅检索公共文档，供公共管理页面使用。
     */
    List<KnowledgeSearchResultDo> searchPublic(String query, int limit);
}
