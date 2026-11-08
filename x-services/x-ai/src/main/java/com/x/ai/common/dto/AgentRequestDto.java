package com.x.ai.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author : xuemingqi
 */
@Data
@Accessors(chain = true)
@Schema(name = "AgentRequestDto", description = "Agent 请求实体")
public class AgentRequestDto {

    @NotBlank(message = "请求不能为空")
    @Schema(name = "request", description = "用户请求")
    private String request;

    @NotBlank(message = "会话id不能为空")
    @Schema(name = "sessionId", description = "会话id")
    private String sessionId;

    @NotNull(message = "模型配置 ID 不能为空")
    @Schema(name = "modelId", description = "用户选择的模型配置 ID")
    private Long modelId;

    @Schema(name = "attachmentIds", description = "本轮消息使用的会话附件 ID")
    private List<Long> attachmentIds;

    @Schema(name = "browserScreenshotEnabled", description = "Web 客户端是否支持授权截图，默认 false")
    private boolean browserScreenshotEnabled;
}
