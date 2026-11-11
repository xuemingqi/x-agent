package com.x.ai.service;

import com.x.ai.common.domain.McpConnectionResultDo;

public interface McpConnectionService {

    /**
     * 在指定用户可见范围内检测已保存的 MCP，返回连接状态和本次发现的工具名称。
     * 临时 HTTP 连接在检测结束后释放。
     */
    McpConnectionResultDo testConnection(String userId, Long id);
}
