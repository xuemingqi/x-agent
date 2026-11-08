package com.x.ai.service;

import com.x.ai.common.domain.KnowledgeDocumentDo;
import com.x.ai.common.domain.KnowledgeDownloadDo;
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
}
