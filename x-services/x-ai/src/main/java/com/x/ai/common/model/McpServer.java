package com.x.ai.common.model;

import com.x.ai.common.enums.McpTransportType;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
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
     * Streamable HTTP Header。
     */
    @ToString.Exclude
    private Map<String, String> headers = new LinkedHashMap<>();

    /**
     * Streamable HTTP 查询参数。
     */
    @ToString.Exclude
    private Map<String, String> queryParameters = new LinkedHashMap<>();

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
