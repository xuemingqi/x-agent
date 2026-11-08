package com.x.ai.service;

import com.x.ai.common.domain.KnowledgeDocumentDo;
import com.x.ai.common.domain.KnowledgeDownloadDo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PublicKnowledgeService {

    /**
     * 查询公共知识库文档。
     *
     * @param actorUserId 操作用户 ID
     * @return 文档列表
     */
    List<KnowledgeDocumentDo> list(String actorUserId);

    /**
     * 上传公共知识库文档，重名文件直接替换。
     *
     * @param actorUserId 操作用户 ID
     * @param files       上传文件
     * @return 已上传文档
     */
    List<KnowledgeDocumentDo> upload(String actorUserId, List<MultipartFile> files);

    /**
     * 写入 Agent 生成的公共知识文本。
     *
     * @param actorUserId 操作用户 ID
     * @param path        公共知识库相对路径
     * @param content     文本内容
     * @return 已写入文档
     */
    KnowledgeDocumentDo writeText(String actorUserId, String path, String content);

    /**
     * 下载公共知识库文档。
     *
     * @param actorUserId 操作用户 ID
     * @param path        公共知识库相对路径
     * @return 下载资源
     */
    KnowledgeDownloadDo download(String actorUserId, String path);

    /**
     * 删除公共知识库文档。
     *
     * @param actorUserId 操作用户 ID
     * @param path        公共知识库相对路径
     * @return 是否删除成功
     */
    boolean delete(String actorUserId, String path);
}
