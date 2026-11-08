package com.x.ai.common.dto;

import io.agentscope.core.tool.ToolParam;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class McpServerToolCreateDto {

    @ToolParam(name = "name", description = "MCP 服务名称，只能包含小写字母、数字、点、下划线和短横线")
    private String name;

    @ToolParam(name = "description", description = "MCP 服务用途描述")
    private String description;

    @ToolParam(name = "transportType", description = "传输方式，只能传整数：1-Streamable HTTP，2-STDIO")
    private int transportType;

    @ToolParam(name = "endpoint", description = "MCP 服务地址，仅当 transportType=1 时传入", required = false)
    private String endpoint;

    @ToolParam(name = "command", description = "启动命令，仅当 transportType=2 时传入", required = false)
    private String command;

    @ToolParam(name = "arguments", description = "启动参数，仅当 transportType=2 时传入", required = false)
    private List<String> arguments;

    @ToolParam(name = "headers", description = "HTTP Header，仅当 transportType=1 时传入", required = false)
    private Map<String, String> headers;

    @ToolParam(name = "queryParameters", description = "HTTP 查询参数，仅当 transportType=1 时传入", required = false)
    private Map<String, String> queryParameters;

    @ToolParam(name = "environment", description = "环境变量，仅当 transportType=2 时传入", required = false)
    private Map<String, String> environment;

    @ToolParam(name = "enabled", description = "是否在 Agent 运行时启用；transportType=2 时必须传 false")
    private boolean enabled;

    @ToolParam(name = "requestTimeoutSeconds", description = "请求超时时间，单位秒，取值范围 1-600")
    private int requestTimeoutSeconds;

    @ToolParam(name = "initializationTimeoutSeconds", description = "初始化超时时间，单位秒，取值范围 1-120")
    private int initializationTimeoutSeconds;
}
