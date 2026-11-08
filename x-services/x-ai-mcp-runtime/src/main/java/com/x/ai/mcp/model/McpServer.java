package com.x.ai.mcp.model;

import com.x.ai.mcp.api.enums.McpTransportType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class McpServer {

    /**
     * MCP 配置 ID。
     */
    private Long id;

    /**
     * MCP 服务名称，同时用于生成工具命名空间。
     */
    private String name;

    /**
     * MCP 传输方式。
     */
    private McpTransportType transportType;

    /**
     * Streamable HTTP 基础地址。
     */
    private String endpoint;

    /**
     * STDIO 启动命令。
     */
    private String command;

    /**
     * STDIO 启动参数。
     */
    private List<String> arguments = List.of();

    /**
     * Streamable HTTP Header。
     */
    private Map<String, String> headers = new LinkedHashMap<>();

    /**
     * Streamable HTTP 查询参数。
     */
    private Map<String, String> queryParameters = new LinkedHashMap<>();

    /**
     * STDIO 环境变量。
     */
    private Map<String, String> environment = new LinkedHashMap<>();

    /**
     * MCP 请求超时时间，单位秒。
     */
    private int requestTimeoutSeconds;

    /**
     * MCP 初始化超时时间，单位秒。
     */
    private int initializationTimeoutSeconds;

    /**
     * 是否在 Agent 运行时启用。
     */
    private boolean enabled;
}
