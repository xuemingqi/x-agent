package com.x.ai.common.domain;

import com.x.ai.common.enums.McpTransportType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.util.Map;

@Data
@Accessors(chain = true)
@Schema(name = "McpServerDo", description = "当前用户可见的 MCP 服务")
public class McpServerDo {

    /**
     * MCP 服务配置 ID。
     */
    @Schema(name = "id", description = "MCP 服务配置 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * MCP 服务名称。
     */
    @Schema(name = "name", description = "MCP 服务名称")
    private String name;

    /**
     * MCP 服务用途描述。
     */
    @Schema(name = "description", description = "MCP 服务用途描述")
    private String description;

    /**
     * MCP 传输方式。
     */
    @Schema(name = "transportType", description = "MCP 传输方式")
    private McpTransportType transportType;

    /**
     * MCP 的 Streamable HTTP 基础地址。
     */
    @Schema(name = "endpoint", description = "MCP 的 Streamable HTTP 基础地址")
    private String endpoint;

    /**
     * 个人 MCP 的 HTTP Header，仅返回 key，value 固定为空。
     */
    @Schema(name = "headers", description = "个人 MCP 的 HTTP Header，仅返回 key，value 固定为空")
    private Map<String, String> headers;

    /**
     * 个人 MCP 的 HTTP 查询参数，仅返回 key，value 固定为空。
     */
    @Schema(name = "queryParameters", description = "个人 MCP 的 HTTP 查询参数，仅返回 key，value 固定为空")
    private Map<String, String> queryParameters;

    /**
     * 是否在 Agent 运行时启用。
     */
    @Schema(name = "enabled", description = "是否在 Agent 运行时启用")
    private boolean enabled;

    /**
     * 是否支持在 Agent 运行时执行。
     */
    @Schema(name = "runtimeSupported", description = "是否支持在 Agent 运行时执行")
    private boolean runtimeSupported;

    /**
     * 请求超时时间，单位秒。
     */
    @Schema(name = "requestTimeoutSeconds", description = "请求超时时间，单位秒")
    private int requestTimeoutSeconds;

    /**
     * 初始化超时时间，单位秒。
     */
    @Schema(name = "initializationTimeoutSeconds", description = "初始化超时时间，单位秒")
    private int initializationTimeoutSeconds;
}
