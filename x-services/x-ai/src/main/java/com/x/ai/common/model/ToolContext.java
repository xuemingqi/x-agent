package com.x.ai.common.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author : xuemingqi
 * @date : 2026/7/21 14:13
 */
@Data
@Accessors(chain = true)
public class ToolContext {

    /**
     * 当前用户 ID。
     */
    private String userId;

    /**
     * 当前 Agent 调用使用的模型配置 ID。
     */
    private Long modelId;

    /**
     * 本轮可检索的知识库快照，包含个人公共库及会话额外关联的库。
     */
    private List<String> knowledgeBaseIds = List.of();
}
