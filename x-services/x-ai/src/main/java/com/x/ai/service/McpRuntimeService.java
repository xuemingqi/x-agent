package com.x.ai.service;

import com.x.ai.common.model.McpRuntimeSnapshot;
import io.agentscope.core.tool.mcp.McpClientWrapper;

import java.util.List;

public interface McpRuntimeService {

    /**
     * 读取当前用户启用的 MCP 配置快照及指纹，使跨节点修改能替换旧运行时。
     */
    McpRuntimeSnapshot load(String userId);

    /**
     * 按同一份配置快照创建 MCP 客户端；调用方负责注册和关闭返回的连接。
     * 普通连接配置错误会跳过当前服务，取消任务则关闭本批次所有未交付连接。
     */
    List<McpClientWrapper> getClients(McpRuntimeSnapshot snapshot);
}
