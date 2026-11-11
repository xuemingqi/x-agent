package com.x.ai.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.x.framework.common.utils.JsonUtil;
import com.x.ai.common.model.McpServer;
import com.x.ai.common.model.McpRuntimeSnapshot;
import com.x.ai.service.McpClientService;
import com.x.ai.service.McpRuntimeService;
import com.x.ai.service.McpServerManagementService;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpRuntimeServiceImpl implements McpRuntimeService {

    private final McpServerManagementService mcpServerManagementService;

    private final McpClientService mcpClientService;

    @Override
    public McpRuntimeSnapshot load(String userId) {
        checkInitializationInterrupted();
        List<McpServer> servers = mcpServerManagementService.listRuntimeServers(userId);
        checkInitializationInterrupted();
        return new McpRuntimeSnapshot(servers, calculateFingerprint(servers));
    }

    /**
     * 固定参数键顺序，使同一配置生成稳定摘要；凭证参与摘要但不会被返回。
     */
    private String calculateFingerprint(List<McpServer> servers) {
        var configurations = servers.stream()
                .map(server -> Map.of("id", server.getId(), "name", server.getName(),
                        "transport", server.getTransportType().getCode(), "endpoint", server.getEndpoint(),
                        "headers", new TreeMap<>(server.getHeaders()),
                        "query", new TreeMap<>(server.getQueryParameters()),
                        "requestTimeout", server.getRequestTimeoutSeconds(),
                        "initializationTimeout", server.getInitializationTimeoutSeconds()))
                .toList();
        return DigestUtil.sha256Hex(JsonUtil.toJsonStrOrThrow(configurations));
    }

    @Override
    public List<McpClientWrapper> getClients(McpRuntimeSnapshot snapshot) {
        checkInitializationInterrupted();
        List<McpClientWrapper> clients = new ArrayList<>();
        boolean completed = false;
        try {
            for (McpServer server : snapshot.getServers()) {
                checkInitializationInterrupted();
                McpClientWrapper client = createClient(server);
                if (client != null) {
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
    private McpClientWrapper createClient(McpServer server) {
        try {
            return mcpClientService.create(server);
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

}
