package com.x.ai.service;

import com.x.ai.common.domain.KnowledgeDocumentDo;
import com.x.ai.common.domain.KnowledgeDownloadDo;
import com.x.ai.common.domain.KnowledgePreviewDo;
import com.x.ai.common.domain.KnowledgeChunkPageDo;
import com.x.ai.common.domain.KnowledgeSearchResultDo;
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

    /**
     * 预览经过解析的文档正文。
     */
    KnowledgePreviewDo preview(String actorUserId, String path);

    /**
     * 分页查看文档切片。
     */
    KnowledgeChunkPageDo chunks(String actorUserId, String path, int page, int size);

    /**
     * 重新解析并索引文档，用于失败重试或模型切换。
     */
    KnowledgeDocumentDo reindex(String actorUserId, String path);

    /**
     * 在当前知识库作用域内执行语义检索。
     */
    List<KnowledgeSearchResultDo> search(String actorUserId, String query, int limit);
}
