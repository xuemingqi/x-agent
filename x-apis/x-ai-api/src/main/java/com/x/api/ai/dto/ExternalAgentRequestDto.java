package com.x.api.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "ExternalAgentRequestDto", description = "外部阻塞式 Agent 请求")
public class ExternalAgentRequestDto {

    @NotBlank(message = "问题内容不能为空")
    @Schema(name = "question", description = "问题内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String question;

    @Size(max = 64, message = "会话 ID 长度不能超过 64")
    @Schema(name = "sessionId", description = "会话 ID，为空时由服务端生成")
    private String sessionId;
}
