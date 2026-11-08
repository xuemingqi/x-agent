package com.x.ai.service;

import com.x.ai.common.domain.KnowledgeLinkDo;
import com.x.ai.common.dto.KnowledgeLinkCreateDto;
import com.x.ai.common.dto.KnowledgeLinkUpdateDto;

import java.util.List;

public interface KnowledgeLinkService {

    /**
     * 查询当前用户的个人在线知识库链接，不向页面返回公共文档。
     *
     * @return 可见链接列表
     */
    List<KnowledgeLinkDo> list();

    /**
     * 为当前用户创建个人在线知识库链接。
     *
     * @param request 链接配置
     * @return 已创建链接
     */
    KnowledgeLinkDo create(KnowledgeLinkCreateDto request);

    /**
     * 修改当前用户拥有的个人在线知识库链接。
     *
     * @param id      链接 ID
     * @param request 新的完整配置
     * @return 已更新链接
     */
    KnowledgeLinkDo update(Long id, KnowledgeLinkUpdateDto request);

    /**
     * 删除当前用户拥有的个人在线知识库链接。
     *
     * @param id 链接 ID
     * @return 是否删除成功
     */
    boolean delete(Long id);
}
