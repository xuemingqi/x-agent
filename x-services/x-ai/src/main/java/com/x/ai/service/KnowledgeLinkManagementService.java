package com.x.ai.service;

import com.x.ai.common.domain.KnowledgeLinkCatalogDo;
import com.x.ai.common.domain.KnowledgeLinkDetailDo;
import com.x.ai.common.domain.KnowledgeLinkDo;
import com.x.ai.common.model.KnowledgeLinkParam;

import java.util.List;

public interface KnowledgeLinkManagementService {

    /**
     * 查询管理页面使用的个人在线知识库链接，不向页面返回公共文档。
     *
     * @param userId 当前用户 ID
     * @return 可见链接列表
     */
    List<KnowledgeLinkDo> list(String userId);

    /**
     * 为指定用户创建个人在线知识库链接。
     *
     * @param userId 当前用户 ID
     * @param param  链接配置
     * @return 已创建链接
     */
    KnowledgeLinkDo create(String userId, KnowledgeLinkParam param);

    /**
     * 修改指定用户拥有的个人在线知识库链接。
     *
     * @param userId 当前用户 ID
     * @param id     链接 ID
     * @param param  新的完整配置
     * @return 已更新链接
     */
    KnowledgeLinkDo update(String userId, Long id, KnowledgeLinkParam param);

    /**
     * 部分修改指定用户拥有的个人链接；null 字段保持原值，folderId 为 0 时移入根目录。
     *
     * @param userId 当前用户 ID
     * @param id     个人链接 ID
     * @param param  至少包含一个待修改字段；名称、介绍和 URL 不允许清空
     * @return 已更新链接
     */
    KnowledgeLinkDo patch(String userId, Long id, KnowledgeLinkParam param);

    /**
     * 删除指定用户拥有的个人在线知识库链接。
     *
     * @param userId 当前用户 ID
     * @param id     链接 ID
     * @return 是否删除成功
     */
    boolean delete(String userId, Long id);

    /**
     * 获取指定用户可见的精简在线知识库目录。
     *
     * @param userId 当前用户 ID
     * @return 文档 ID、名称和简短介绍
     */
    List<KnowledgeLinkCatalogDo> catalog(String userId);

    /**
     * 获取指定用户可见的单个在线知识库链接。
     *
     * @param userId 当前用户 ID
     * @param id     链接 ID
     * @return 文档 ID、名称和实际链接
     */
    KnowledgeLinkDetailDo getVisible(String userId, Long id);

    /**
     * 查询公共在线知识库链接。
     */
    List<KnowledgeLinkDo> listPublic();

    /**
     * 创建公共在线知识库链接。
     */
    KnowledgeLinkDo createPublic(String actorUserId, KnowledgeLinkParam param);

    /**
     * 修改公共在线知识库链接。
     */
    KnowledgeLinkDo updatePublic(Long id, KnowledgeLinkParam param);

    /**
     * 删除公共在线知识库链接。
     */
    boolean deletePublic(Long id);
}
