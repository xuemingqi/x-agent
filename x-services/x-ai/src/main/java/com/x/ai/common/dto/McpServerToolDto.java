package com.x.ai.common.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 对话工具专用 MCP 配置输出，ID 使用字符串，枚举使用与工具入参一致的整数编码。
 */
@Data
@Accessors(chain = true)
public class McpServerToolDto {

    /**
     * MCP 配置 ID，字符串形式保留完整精度。
     */
    private String id;

    /**
     * Agent 工具命名空间，创建后保持不变。
     */
    private String name;

    /**
     * MCP 服务用途说明。
     */
    private String description;

    /**
     * 传输方式编码，当前仅支持 1（Streamable HTTP）。
     */
    private Integer transportType;

    /**
     * 远程 MCP 的 HTTP/HTTPS 地址。
     */
    private String endpoint;

    /**
     * 已配置的 HTTP 请求头，仅返回字段名，值固定为空。
     */
    private Map<String, String> headers;

    /**
     * 已配置的 HTTP 查询参数，仅返回字段名，值固定为空。
     */
    private Map<String, String> queryParameters;

    /**
     * 是否将此配置提供给 Agent 使用。
     */
    private boolean enabled;

    /**
     * 当前版本是否支持连接该配置。
     */
    private boolean runtimeSupported;

    /**
     * 单次 MCP 请求超时，单位秒，范围 1 到 600。
     */
    private int requestTimeoutSeconds;

    /**
     * MCP 初始化超时，单位秒，范围 1 到 120。
     */
    private int initializationTimeoutSeconds;
}
