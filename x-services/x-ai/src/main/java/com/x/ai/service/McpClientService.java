package com.x.ai.service;

import com.x.ai.common.model.McpServer;
import io.agentscope.core.tool.mcp.McpClientWrapper;

public interface McpClientService {

    /**
     * 根据 MCP 运行时模型创建尚未初始化的 AgentScope 客户端。
     *
     * @param server MCP 运行时模型
     * @return MCP 客户端
     */
    McpClientWrapper create(McpServer server);
}
