package com.x.ai.common.dto;

import io.agentscope.core.tool.ToolParam;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
public class McpServerToolCreateDto {

    /**
     * Agent 工具命名空间，创建后保持不变。
     */
    @ToolParam(name = "name", description = "MCP 服务名称，只能包含小写字母、数字、点、下划线和短横线")
    private String name;

    /**
     * MCP 服务用途说明。
     */
    @ToolParam(name = "description", description = "MCP 服务用途描述")
    private String description;

    /**
     * 传输方式编码，当前仅支持 1（Streamable HTTP）。
     */
    @ToolParam(name = "transportType", description = "传输方式，只能传整数：1-Streamable HTTP")
    private int transportType;

    /**
     * 远程 MCP 的 HTTP/HTTPS 地址。
     */
    @ToolParam(name = "endpoint", description = "MCP HTTP/HTTPS 服务地址")
    private String endpoint;

    /**
     * 访问远程 MCP 使用的 HTTP 请求头。
     */
    @ToolParam(name = "headers", description = "HTTP Header", required = false)
    private Map<String, String> headers;

    /**
     * 访问远程 MCP 使用的 HTTP 查询参数。
     */
    @ToolParam(name = "queryParameters", description = "HTTP 查询参数", required = false)
    private Map<String, String> queryParameters;

    /**
     * 是否将此配置提供给 Agent 使用。
     */
    @ToolParam(name = "enabled", description = "是否在 Agent 运行时启用")
    private boolean enabled;

    /**
     * 单次 MCP 请求超时，单位秒，范围 1 到 600。
     */
    @ToolParam(name = "requestTimeoutSeconds", description = "请求超时时间，单位秒，取值范围 1-600")
    private int requestTimeoutSeconds;

    /**
     * MCP 初始化超时，单位秒，范围 1 到 120。
     */
    @ToolParam(name = "initializationTimeoutSeconds", description = "初始化超时时间，单位秒，取值范围 1-120")
    private int initializationTimeoutSeconds;
}
