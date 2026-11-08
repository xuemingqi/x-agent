package com.x.ai.mcp.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "McpUserCredentialUpdateDto", description = "配置当前用户的公司内部公共 MCP 凭证")
public class McpUserCredentialUpdateDto {

    @NotBlank(message = "用户凭证不能为空")
    @Size(max = 2048, message = "用户凭证不能超过 2048 个字符")
    @Schema(name = "credential", description = "当前用户的 MCP 凭证值")
    private String credential;
}
