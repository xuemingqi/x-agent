package com.x.ai.mcp.runtime;

import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.ai.mcp.api.domain.McpRuntimeServerDo;
import com.x.ai.mcp.api.enums.McpScope;
import com.x.ai.mcp.config.McpRuntimeProperties;
import com.x.ai.mcp.db.entity.AgentMcpServer;
import com.x.ai.mcp.db.service.AgentMcpServerIService;
import com.x.ai.mcp.service.McpUserCredentialService;
import jakarta.servlet.ServletContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class McpRuntimeRegistry implements AutoCloseable {

    private final AgentMcpServerIService agentMcpServerIService;

    private final McpUserCredentialService userCredentialService;

    private final McpRuntimeFactory runtimeFactory;

    private final McpRuntimeProperties properties;

    private final ServletContext servletContext;

    private final ConcurrentMap<String, ConcurrentMap<Long, McpProxyRuntime>> runtimes = new ConcurrentHashMap<>();


    /**
     * 查询当前用户可用的公共和个人 MCP 运行描述。
     *
     * @param userId 用户 ID
     * @return MCP 运行描述
     */
    public List<McpRuntimeServerDo> list(String userId) {
        List<AgentMcpServer> servers = agentMcpServerIService.visibleQuery(userId)
                .eq(AgentMcpServer::getEnabled, true)
                .orderByAsc(AgentMcpServer::getScope)
                .orderByAsc(AgentMcpServer::getId)
                .list();
        Set<Long> userCredentialRequiredServerIds = servers.stream()
                .filter(userCredentialService::requiresUserCredential)
                .map(AgentMcpServer::getId)
                .collect(Collectors.toUnmodifiableSet());
        Set<Long> configuredServerIds = userCredentialService.configuredServerIds(userId, servers);
        return servers.stream()
                .filter(server -> server.getScope() == McpScope.USER
                        || !userCredentialRequiredServerIds.contains(server.getId())
                        || configuredServerIds.contains(server.getId()))
                .map(server -> new McpRuntimeServerDo()
                        .setId(server.getId())
                        .setName(server.getName())
                        .setRequestTimeoutSeconds(server.getRequestTimeoutSeconds())
                        .setInitializationTimeoutSeconds(server.getInitializationTimeoutSeconds())
                        .setRuntimePath(servletContext.getContextPath() + properties.runtimePath(userId, server.getId())))
                .toList();
    }

    /**
     * 获取或创建当前用户和 MCP 配置独享的后端进程及 HTTP 会话运行实例。
     *
     * @param userId          用户 ID
     * @param configurationId MCP 配置 ID
     * @return MCP 代理运行实例
     */
    public McpProxyRuntime get(String userId, Long configurationId) {
        while (true) {
            ConcurrentMap<Long, McpProxyRuntime> userRuntimes = runtimes.computeIfAbsent(userId,
                    ignored -> new ConcurrentHashMap<>());
            synchronized (userRuntimes) {
                if (runtimes.get(userId) != userRuntimes) {
                    continue;
                }
                McpProxyRuntime runtime = userRuntimes.get(configurationId);
                if (runtime != null) {
                    return runtime;
                }
                AgentMcpServer server = enabledVisibleServer(userId, configurationId);
                runtime = runtimeFactory.create(userId, userCredentialService.toRuntimeServer(userId, server));
                userRuntimes.put(configurationId, runtime);
                return runtime;
            }
        }
    }

    /**
     * 配置修改或删除后只关闭该用户自己的运行实例，不处理共享安装缓存。
     *
     * @param userId          用户 ID
     * @param configurationId MCP 配置 ID
     */
    public void evict(String userId, Long configurationId) {
        ConcurrentMap<Long, McpProxyRuntime> userRuntimes = runtimes.get(userId);
        if (userRuntimes == null) {
            return;
        }
        McpProxyRuntime runtime;
        synchronized (userRuntimes) {
            if (runtimes.get(userId) != userRuntimes) {
                return;
            }
            runtime = userRuntimes.remove(configurationId);
            if (userRuntimes.isEmpty()) {
                runtimes.remove(userId, userRuntimes);
            }
        }
        if (runtime != null) {
            runtime.close();
        }
    }

    /**
     * 用户更新共享凭证后关闭同一凭证组的全部运行实例。
     *
     * @param userId 用户 ID
     * @param credentialGroup 凭证组
     */
    public void evictCredentialGroup(String userId, String credentialGroup) {
        agentMcpServerIService.lambdaQuery()
                .select(AgentMcpServer::getId)
                .eq(AgentMcpServer::getScope, McpScope.INTERNAL_PUBLIC)
                .eq(AgentMcpServer::getCredentialGroup, credentialGroup)
                .list()
                .stream()
                .map(AgentMcpServer::getId)
                .forEach(serverId -> evict(userId, serverId));
    }

    /**
     * 公共配置变化后关闭所有用户持有的对应运行实例。
     */
    public void evictConfiguration(Long configurationId) {
        List.copyOf(runtimes.keySet()).forEach(userId -> evict(userId, configurationId));
    }

    @Override
    public void close() {
        runtimes.values().stream()
                .flatMap(userRuntimes -> userRuntimes.values().stream())
                .forEach(McpProxyRuntime::close);
        runtimes.clear();
    }

    /**
     * 查询当前用户可见且已经启用的配置，阻止根据其他用户 ID 启动进程。
     */
    private AgentMcpServer enabledVisibleServer(String userId, Long configurationId) {
        AgentMcpServer server = agentMcpServerIService.visibleQuery(userId)
                .eq(AgentMcpServer::getId, configurationId)
                .eq(AgentMcpServer::getEnabled, true)
                .one();
        if (server == null) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_NOT_FOUND);
        }
        return server;
    }
}
