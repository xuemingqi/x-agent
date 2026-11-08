package com.x.ai.service;

import com.x.ai.common.domain.KnowledgeLinkCatalogDo;
import com.x.ai.common.domain.KnowledgeLinkDetailDo;
import com.x.ai.common.model.ToolContext;

import java.util.List;

public interface KnowledgeLinkToolService {

    /**
     * 获取当前工具用户可见的在线知识库目录。
     *
     * @param toolContext 可信工具上下文
     * @return 文档 ID、名称和简短介绍
     */
    List<KnowledgeLinkCatalogDo> catalog(ToolContext toolContext);

    /**
     * 获取当前工具用户可见的单个在线知识库链接。
     *
     * @param toolContext 可信工具上下文
     * @param id          链接 ID
     * @return 文档 ID、名称和实际链接
     */
    KnowledgeLinkDetailDo get(ToolContext toolContext, Long id);
}
