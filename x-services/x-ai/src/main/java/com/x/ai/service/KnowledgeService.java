package com.x.ai.service;

import com.x.ai.common.domain.KnowledgeDocumentDo;
import com.x.ai.common.domain.KnowledgeDownloadDo;
import com.x.ai.common.domain.KnowledgePreviewDo;
import com.x.ai.common.domain.KnowledgeChunkPageDo;
import com.x.ai.common.domain.KnowledgeSearchResultDo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeService {

    /**
     * 查询当前用户知识库中的全部文档。
     *
     * @return 文档列表
     */
    List<KnowledgeDocumentDo> list();

    /**
     * 上传文档到当前用户知识库，重名文件直接替换。
     *
     * @param files 上传文件
     * @return 已上传文档
     */
    List<KnowledgeDocumentDo> upload(List<MultipartFile> files);

    /**
     * 下载当前用户知识库中的指定文档。
     *
     * @param path 用户知识库相对路径
     * @return 下载资源
     */
    KnowledgeDownloadDo download(String path);

    /**
     * 删除当前用户知识库中的指定文档。
     *
     * @param path 用户知识库相对路径
     * @return 是否删除成功
     */
    boolean delete(String path);

    /**
     * 预览经过解析的文档正文。
     */
    KnowledgePreviewDo preview(String path);

    /**
     * 分页查看文档切片。
     */
    KnowledgeChunkPageDo chunks(String path, int page, int size);

    /**
     * 重新解析并索引文档，用于失败重试或模型切换。
     */
    KnowledgeDocumentDo reindex(String path);

    /**
     * 在当前知识库作用域内执行语义检索。
     */
    List<KnowledgeSearchResultDo> search(String query, int limit);
}
