package com.x.ai.service.impl;

import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.mcp.api.McpRuntimeApi;
import com.x.ai.mcp.api.domain.McpRuntimeServerDo;
import com.x.ai.service.McpRuntimeService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerUriTools;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpRuntimeServiceImpl implements McpRuntimeService {

    private final McpRuntimeApi mcpRuntimeApi;

    private final LoadBalancerClient loadBalancerClient;

    @Override
    public List<McpClientWrapper> getVisibleClients(String userId, ResourceEnablement enablement) {
        checkInitializationInterrupted();
        List<McpClientWrapper> clients = new ArrayList<>();
        Set<String> clientNames = new HashSet<>();
        List<McpRuntimeServerDo> servers = mcpRuntimeApi.listRuntimeServers(userId);
        boolean completed = false;
        try {
            for (McpRuntimeServerDo server : servers) {
                checkInitializationInterrupted();
                if (!enablement.isMcpEnabled(server.getId())) {
                    continue;
                }
                if (clientNames.contains(server.getName())) {
                    log.warn("Skip duplicated MCP namespace, serverId={}, name={}", server.getId(), server.getName());
                    continue;
                }
                McpClientWrapper client = createClient(server);
                if (client != null) {
                    clientNames.add(server.getName());
                    clients.add(client);
                }
            }
            checkInitializationInterrupted();
            List<McpClientWrapper> visibleClients = List.copyOf(clients);
            completed = true;
            return visibleClients;
        } finally {
            if (!completed) {
                clients.forEach(this::closeMcpClient);
            }
        }
    }

    /**
     * 创建单个 MCP 客户端，失败时记录不含凭证的上下文并跳过。
     */
    private McpClientWrapper createClient(McpRuntimeServerDo server) {
        try {
            String endpoint = resolveRuntimeEndpoint(server.getRuntimePath());
            McpClientWrapper client = McpClientBuilder.create(server.getName())
                    .streamableHttpTransport(endpoint)
                    .timeout(proxyRequestTimeout(server))
                    .initializationTimeout(proxyInitializationTimeout(server))
                    .buildAsync()
                    .block();
            if (client == null) {
                throw new XException(ResponseCodeEnum.AGENT_MCP_CONNECTION_ERROR);
            }
            return client;
        } catch (RuntimeException error) {
            if (Thread.currentThread().isInterrupted()) {
                throw error;
            }
            log.warn("Skip invalid MCP server, serverId={}, name={}, errorType={}",
                    server.getId(), server.getName(), error.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 取消准备任务时及时退出，保留中断标志供上层完成资源清理。
     */
    private void checkInitializationInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("MCP initialization interrupted");
        }
    }

    /**
     * 构建批次未交付时释放已建连接，单个关闭失败不阻断后续清理。
     */
    private void closeMcpClient(McpClientWrapper client) {
        try {
            client.close();
        } catch (RuntimeException error) {
            log.warn("Failed to close MCP client, name={}", client.getName(), error);
        }
    }

    /**
     * 创建连接时从注册中心选择一次实例，后续 MCP 会话固定使用该地址，不逐请求负载均衡。
     * 运行路径沿用 Runtime 返回值，由框架重建 URI，避免重复编码用户标识。
     */
    private String resolveRuntimeEndpoint(String runtimePath) {
        ServiceInstance instance = loadBalancerClient.choose(McpRuntimeApi.SERVICE_NAME);
        if (instance == null) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_CONNECTION_ERROR);
        }
        return LoadBalancerUriTools.reconstructURI(instance, URI.create(runtimePath)).toString();
    }

    /**
     * 为代理转发开销保留一秒，避免后端刚好在配置上限完成时前置客户端先超时。
     */
    private Duration proxyRequestTimeout(McpRuntimeServerDo server) {
        return Duration.ofSeconds((long) server.getRequestTimeoutSeconds() + 1L);
    }

    /**
     * 首次访问需要先初始化后端并发现工具，因此覆盖初始化和一次工具列表请求。
     */
    private Duration proxyInitializationTimeout(McpRuntimeServerDo server) {
        long seconds = (long) server.getInitializationTimeoutSeconds()
                + server.getRequestTimeoutSeconds() + 2L;
        return Duration.ofSeconds(seconds);
    }
}
