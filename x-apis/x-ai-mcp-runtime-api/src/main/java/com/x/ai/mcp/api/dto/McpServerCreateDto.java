package com.x.ai.mcp.api.dto;

import com.x.ai.mcp.api.enums.McpTransportType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
@Schema(name = "McpServerCreateDto", description = "创建个人 MCP 服务")
public class McpServerCreateDto {

    @NotBlank(message = "MCP 名称不能为空")
    @Size(max = 64, message = "MCP 名称不能超过 64 个字符")
    @Schema(name = "name", description = "MCP 服务名称")
    private String name;

    @NotBlank(message = "MCP 描述不能为空")
    @Schema(name = "description", description = "MCP 服务用途描述")
    private String description;

    @NotNull(message = "MCP 传输方式不能为空")
    @Schema(name = "transportType", description = "传输方式，1：Streamable HTTP，2：STDIO")
    private McpTransportType transportType;

    @Schema(name = "endpoint", description = "Streamable HTTP 服务地址，可携带查询参数")
    private String endpoint;

    @Schema(name = "command", description = "STDIO 启动命令")
    private String command;

    @Schema(name = "arguments", description = "STDIO 启动参数")
    private List<String> arguments;

    @Schema(name = "headers", description = "Streamable HTTP Header")
    private Map<String, String> headers;

    @Schema(name = "queryParameters", description = "Streamable HTTP 查询参数")
    private Map<String, String> queryParameters;

    @Schema(name = "environment", description = "STDIO 环境变量")
    private Map<String, String> environment;

    @NotNull(message = "MCP 启用状态不能为空")
    @Schema(name = "enabled", description = "是否在 Agent 运行时启用")
    private Boolean enabled;

    @NotNull(message = "MCP 请求超时时间不能为空")
    @Schema(name = "requestTimeoutSeconds", description = "请求超时时间，单位秒")
    private Integer requestTimeoutSeconds;

    @NotNull(message = "MCP 初始化超时时间不能为空")
    @Schema(name = "initializationTimeoutSeconds", description = "初始化超时时间，单位秒")
    private Integer initializationTimeoutSeconds;
}
