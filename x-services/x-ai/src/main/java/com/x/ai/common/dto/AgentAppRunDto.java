package com.x.ai.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AgentAppRunDto {

    /**
     * 页面加载的发布版本，变化后必须重新加载应用。
     */
    @NotBlank
    @Size(max = 64)
    private String version;

    /**
     * 独立的一次性 Agent 任务，不继承聊天历史。
     */
    @NotBlank
    @Size(max = 100_000)
    private String request;

    /**
     * 当前用户拥有的模型配置 ID；为空时使用个人默认模型。
     */
    private Long modelId;
}
