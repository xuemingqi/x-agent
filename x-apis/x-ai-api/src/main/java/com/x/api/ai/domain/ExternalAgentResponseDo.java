package com.x.api.ai.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "ExternalAgentResponseDo", description = "外部 Agent 阻塞调用结果")
public class ExternalAgentResponseDo {

    @Schema(name = "text", description = "Agent 文本结果")
    private String text;

    @Schema(name = "sessionId", description = "实际使用的会话 ID")
    private String sessionId;

    @Schema(name = "userId", description = "API Key 所属用户 ID")
    private String userId;
}
