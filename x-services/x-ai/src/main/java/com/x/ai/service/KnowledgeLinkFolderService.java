package com.x.ai.service;

import com.x.ai.common.domain.KnowledgeLinkFolderDo;
import com.x.ai.common.dto.KnowledgeLinkFolderCreateDto;
import com.x.ai.common.dto.KnowledgeLinkFolderUpdateDto;

import java.util.List;

public interface KnowledgeLinkFolderService {

    /**
     * 查询当前用户的个人在线知识库文件夹。
     *
     * @return 当前用户的个人文件夹列表
     */
    List<KnowledgeLinkFolderDo> list();

    /**
     * 在当前用户的个人目录下创建文件夹。
     *
     * @param request 文件夹名称及个人父目录
     * @return 已创建的文件夹
     */
    KnowledgeLinkFolderDo create(KnowledgeLinkFolderCreateDto request);

    /**
     * 重命名当前用户的个人文件夹。
     *
     * @param id      个人文件夹 ID
     * @param request 新文件夹名称
     * @return 更新后的文件夹
     */
    KnowledgeLinkFolderDo rename(Long id, KnowledgeLinkFolderUpdateDto request);

    /**
     * 删除当前用户的个人空文件夹。
     *
     * @param id 个人文件夹 ID，不允许删除根目录
     * @return 是否删除成功
     */
    boolean delete(Long id);
}
