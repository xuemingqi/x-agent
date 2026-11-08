package com.x.ai.mcp.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(name = "PublicMcpServerUpdateDto", description = "修改公共 MCP 服务")
public class PublicMcpServerUpdateDto extends McpServerUpdateDto {
}
