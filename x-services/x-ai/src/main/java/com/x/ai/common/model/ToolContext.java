package com.x.ai.common.model;

import lombok.Data;
import lombok.experimental.Accessors;

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
}