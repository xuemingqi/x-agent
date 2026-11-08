package com.x.ai.service;

import com.x.ai.common.domain.McpConnectionResultDo;
import com.x.ai.mcp.api.enums.McpScope;

public interface McpConnectionService {

    /**
     * 在指定用户可见范围内检测已保存的 MCP，返回连接状态和本次发现的工具名称。
     * 临时连接由 MCP Runtime 在检测结束后释放。
     */
    McpConnectionResultDo testConnection(String userId, McpScope scope, Long id);
}
