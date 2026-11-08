package com.x.ai.mcp.api.domain;

import com.x.ai.mcp.api.enums.McpScope;
import com.x.ai.mcp.api.enums.McpTransportType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
@Schema(name = "McpServerDo", description = "当前用户可见的 MCP 服务")
public class McpServerDo {

    @Schema(name = "id", description = "MCP 服务配置 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(name = "name", description = "MCP 服务名称")
    private String name;

    @Schema(name = "description", description = "MCP 服务用途描述")
    private String description;

    @Schema(name = "scope", description = "MCP 作用域")
    private McpScope scope;

    @Schema(name = "transportType", description = "MCP 传输方式")
    private McpTransportType transportType;

    @Schema(name = "endpoint", description = "个人 MCP 的 Streamable HTTP 基础地址，公共 MCP 不返回")
    private String endpoint;

    @Schema(name = "command", description = "个人 MCP 的 STDIO 启动命令，公共 MCP 不返回")
    private String command;

    @Schema(name = "arguments", description = "个人 MCP 的 STDIO 启动参数，公共 MCP 不返回")
    private List<String> arguments;

    @Schema(name = "headers", description = "个人 MCP 的 HTTP Header，仅返回 key，value 固定为空")
    private Map<String, String> headers;

    @Schema(name = "queryParameters", description = "个人 MCP 的 HTTP 查询参数，仅返回 key，value 固定为空")
    private Map<String, String> queryParameters;

    @Schema(name = "environment", description = "个人 MCP 的 STDIO 环境变量，仅返回 key，value 固定为空")
    private Map<String, String> environment;

    @Schema(name = "enabled", description = "是否在 Agent 运行时启用")
    private boolean enabled;

    @Schema(name = "userCredentialConfigured", description = "当前用户是否已配置公司内部公共 MCP 凭证")
    private boolean userCredentialConfigured;

    @Schema(name = "userCredentialRequired", description = "是否要求当前用户配置独立凭证")
    private boolean userCredentialRequired;

    /**
     * 当前用户的启用偏好，不改变公共 MCP 配置及其他用户状态。
     */
    @Schema(description = "当前用户是否启用，不改变 MCP 服务配置")
    private boolean userEnabled = true;

    @Schema(name = "editable", description = "当前用户是否可编辑")
    private boolean editable;

    @Schema(name = "runtimeSupported", description = "是否支持在 Agent 运行时执行")
    private boolean runtimeSupported;

    @Schema(name = "requestTimeoutSeconds", description = "请求超时时间，单位秒")
    private int requestTimeoutSeconds;

    @Schema(name = "initializationTimeoutSeconds", description = "初始化超时时间，单位秒")
    private int initializationTimeoutSeconds;
}
