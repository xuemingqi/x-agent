package com.x.ai.common.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * 对话工具专用 MCP 配置输出，ID 使用字符串，枚举使用与工具入参一致的整数编码。
 */
@Data
@Accessors(chain = true)
public class McpServerToolDto {

    private String id;

    private String name;

    private String description;

    private int scope;

    private Integer transportType;

    private String endpoint;

    private String command;

    private List<String> arguments;

    private Map<String, String> headers;

    private Map<String, String> queryParameters;

    private Map<String, String> environment;

    private boolean enabled;

    private boolean editable;

    private boolean runtimeSupported;

    private int requestTimeoutSeconds;

    private int initializationTimeoutSeconds;
}
