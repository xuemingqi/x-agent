package com.x.ai.common.model;

import com.x.ai.skill.SkillRuntimeWorkspace;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 缓存拥有 Agent、MCP 连接和生成工作区，统一释放其生命周期。
 */
@Getter
@Slf4j
@RequiredArgsConstructor
public class AgentRuntime {

    /**
     * 缓存持有的 Agent 实例，由本运行时统一关闭。
     */
    private final HarnessAgent agent;

    /**
     * 本次构建成功注册的 MCP 客户端，需要随 Agent 释放。
     */
    private final List<McpClientWrapper> mcpClients;

    /**
     * 构建时的配置快照，用于判断后续请求是否可复用该运行时。
     */
    private final AgentRuntimeConfiguration configuration;

    /**
     * 仅属于此 Agent 的 Skill 运行文件，关闭时删除。
     */
    private final SkillRuntimeWorkspace skillWorkspace;


    /**
     * 依次释放 MCP 连接、Agent 和生成工作区，单个连接关闭失败不阻断后续清理。
     */
    public void shutdown() {
        mcpClients.forEach(this::closeMcpClient);
        try {
            agent.close();
        } catch (RuntimeException error) {
            log.warn("Failed to close AgentScope runtime", error);
        } finally {
            skillWorkspace.close();
        }
    }

    /**
     * 独立释放一个 MCP 连接，记录失败并允许其他资源继续关闭。
     */
    private void closeMcpClient(McpClientWrapper client) {
        try {
            client.close();
        } catch (RuntimeException error) {
            log.warn("Failed to close MCP client: {}", client.getName(), error);
        }
    }
}
