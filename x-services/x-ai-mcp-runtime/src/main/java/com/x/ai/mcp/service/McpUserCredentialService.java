package com.x.ai.mcp.service;

import com.x.ai.mcp.db.entity.AgentMcpServer;
import com.x.ai.mcp.model.McpServer;

import java.util.Collection;
import java.util.Set;

public interface McpUserCredentialService {

    /**
     * 批量查询当前用户已配置凭证的公司内部公共 MCP ID，同一凭证组只查询一次。
     *
     * @param userId  用户 ID
     * @param servers 公司内部公共 MCP 配置
     * @return 已配置用户凭证的 MCP ID
     */
    Set<Long> configuredServerIds(String userId, Collection<AgentMcpServer> servers);

    /**
     * 保存当前用户的共享 MCP 凭证。
     *
     * @param userId     用户 ID
     * @param server     公司内部公共 MCP 配置
     * @param credential 用户凭证值
     * @return 本次更新的凭证组
     */
    String save(String userId, AgentMcpServer server, String credential);

    /**
     * 将当前用户凭证合并到公司内部公共 MCP 的运行时配置。
     *
     * @param userId 用户 ID
     * @param server MCP 持久化配置
     * @return 当前用户可用的 MCP 运行时配置
     */
    McpServer toRuntimeServer(String userId, AgentMcpServer server);

    /**
     * 判断 MCP 是否要求当前用户提供独立凭证。
     *
     * @param server MCP 持久化配置
     * @return 是否需要用户凭证
     */
    boolean requiresUserCredential(AgentMcpServer server);
}
