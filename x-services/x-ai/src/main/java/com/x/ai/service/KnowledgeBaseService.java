package com.x.ai.service;

import com.x.ai.common.domain.KnowledgeBaseDo;
import com.x.ai.common.dto.KnowledgeBaseDto;

import java.util.List;

/**
 * 用户独立知识库及会话检索范围管理。
 */
public interface KnowledgeBaseService {

    /**
     * 确保个人公共知识库存在并列出当前用户的全部知识库。
     */
    List<KnowledgeBaseDo> list();

    /**
     * 为当前用户新建知识库。
     */
    KnowledgeBaseDo create(KnowledgeBaseDto request);

    /**
     * 编辑当前用户的知识库名称及说明。
     */
    KnowledgeBaseDo update(String id, KnowledgeBaseDto request);

    /**
     * 删除当前用户的非公共知识库及其文档与切片。
     */
    boolean delete(String id);

    /**
     * 校验指定知识库均属于执行用户，未知或其他用户的 ID 统一返回不存在。
     */
    List<KnowledgeBaseDo> requireOwned(String userId, List<String> ids);

    /**
     * 返回个人公共库和仍存在的自选知识库 ID，忽略已删除或不属于该用户的 ID。
     */
    List<String> searchableBaseIds(String userId, List<String> selectedIds);
}
