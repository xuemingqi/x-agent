package com.x.ai.common.dto;

import com.x.ai.common.enums.McpTransportType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
@Schema(name = "McpServerCreateDto", description = "创建个人 MCP 服务")
public class McpServerCreateDto {

    /**
     * MCP 服务名称。
     */
    @NotBlank(message = "MCP 名称不能为空")
    @Size(max = 64, message = "MCP 名称不能超过 64 个字符")
    @Schema(name = "name", description = "MCP 服务名称")
    private String name;

    /**
     * MCP 服务用途描述。
     */
    @NotBlank(message = "MCP 描述不能为空")
    @Schema(name = "description", description = "MCP 服务用途描述")
    private String description;

    /**
     * 传输方式，1：Streamable HTTP。
     */
    @NotNull(message = "MCP 传输方式不能为空")
    @Schema(name = "transportType", description = "传输方式，1：Streamable HTTP")
    private McpTransportType transportType;

    /**
     * Streamable HTTP 服务地址，可携带查询参数。
     */
    @Schema(name = "endpoint", description = "Streamable HTTP 服务地址，可携带查询参数")
    private String endpoint;

    /**
     * Streamable HTTP Header。
     */
    @Schema(name = "headers", description = "Streamable HTTP Header")
    private Map<String, String> headers;

    /**
     * Streamable HTTP 查询参数。
     */
    @Schema(name = "queryParameters", description = "Streamable HTTP 查询参数")
    private Map<String, String> queryParameters;

    /**
     * 是否在 Agent 运行时启用。
     */
    @NotNull(message = "MCP 启用状态不能为空")
    @Schema(name = "enabled", description = "是否在 Agent 运行时启用")
    private Boolean enabled;

    /**
     * 请求超时时间，单位秒。
     */
    @NotNull(message = "MCP 请求超时时间不能为空")
    @Schema(name = "requestTimeoutSeconds", description = "请求超时时间，单位秒")
    private Integer requestTimeoutSeconds;

    /**
     * 初始化超时时间，单位秒。
     */
    @NotNull(message = "MCP 初始化超时时间不能为空")
    @Schema(name = "initializationTimeoutSeconds", description = "初始化超时时间，单位秒")
    private Integer initializationTimeoutSeconds;
}
