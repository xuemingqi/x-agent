package com.x.ai.service;

import com.x.ai.common.model.ResourceEnablement;
import io.agentscope.core.tool.mcp.McpClientWrapper;

import java.util.List;

public interface McpRuntimeService {

    /**
     * 获取当前用户可用的公共和个人 MCP 客户端。
     * 重复命名空间和无法创建的客户端会被跳过，返回的客户端由调用方负责注册和关闭。
     *
     * @param userId 用户 ID
     * @return 当前用户可用的 MCP 客户端
     */
    List<McpClientWrapper> getVisibleClients(String userId, ResourceEnablement enablement);
}
