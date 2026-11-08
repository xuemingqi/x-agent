package com.x.ai.mcp.api.dto;

import com.x.ai.mcp.api.enums.McpScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(name = "PublicMcpServerCreateDto", description = "创建公共 MCP 服务")
public class PublicMcpServerCreateDto extends McpServerCreateDto {

    @NotNull(message = "公共 MCP 作用域不能为空")
    @Schema(name = "scope", description = "作用域，1：公共，3：公司内部公共")
    private McpScope scope;
}
