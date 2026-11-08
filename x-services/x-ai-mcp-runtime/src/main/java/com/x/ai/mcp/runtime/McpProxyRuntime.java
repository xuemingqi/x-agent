package com.x.ai.mcp.runtime;

import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class McpProxyRuntime implements AutoCloseable {

    private final McpClientWrapper client;

    private final HttpServletStreamableServerTransportProvider transportProvider;

    private final McpAsyncServer server;

    private final AtomicBoolean closed = new AtomicBoolean();


    public McpProxyRuntime(McpClientWrapper client, HttpServletStreamableServerTransportProvider transportProvider,
                           McpAsyncServer server) {
        this.client = client;
        this.transportProvider = transportProvider;
        this.server = server;
    }

    /**
     * 将当前 HTTP MCP 请求交给该用户配置独享的 Streamable HTTP Provider。
     */
    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (closed.get()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        transportProvider.service(request, response);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            server.close();
        } catch (RuntimeException error) {
            log.warn("Failed to close MCP proxy server, name={}", client.getName(), error);
        }
        try {
            client.close();
        } catch (RuntimeException error) {
            log.warn("Failed to close MCP backend client, name={}", client.getName(), error);
        }
    }
}
