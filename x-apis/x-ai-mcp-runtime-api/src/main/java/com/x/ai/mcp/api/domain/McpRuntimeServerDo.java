package com.x.ai.mcp.api.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@Accessors(chain = true)
@Schema(name = "McpRuntimeServerDo", description = "MCP 运行时服务器信息")
public class McpRuntimeServerDo {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(name = "id", description = "MCP 服务配置 ID")
    private Long id;

    @Schema(name = "name", description = "MCP 服务名称")
    private String name;

    @Schema(name = "runtimePath", description = "MCP Streamable HTTP 运行路径")
    private String runtimePath;

    @Schema(name = "requestTimeoutSeconds", description = "请求超时时间，单位秒")
    private int requestTimeoutSeconds;

    @Schema(name = "initializationTimeoutSeconds", description = "初始化超时时间，单位秒")
    private int initializationTimeoutSeconds;
}
