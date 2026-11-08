package com.x.ai.common.domain;

import com.x.ai.mcp.api.enums.McpScope;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * MCP 连接检测的共享业务结果，入口各自转换为 Web 或工具输出。
 */
@Data
@Accessors(chain = true)
public class McpConnectionResultDo {

    private Long id;

    private McpScope scope;

    private boolean connected;

    private List<String> toolNames;
}
