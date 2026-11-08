package com.x.ai.mcp.runtime;

import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.ai.mcp.config.McpRuntimeProperties;
import com.x.ai.mcp.model.McpServer;
import com.x.ai.mcp.service.McpClientService;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class McpRuntimeFactory {

    private static final String SERVER_NAME = "x-ai-mcp-runtime";

    private static final String SERVER_VERSION = "1.0.0";

    private final McpClientService mcpClientService;

    private final McpRuntimeProperties properties;


    /**
     * 初始化后端 MCP 客户端并建立对应的 Streamable HTTP 服务端运行实例。
     */
    public McpProxyRuntime create(String userId, McpServer configuration) {
        McpClientWrapper client = mcpClientService.create(configuration);
        try {
            List<McpSchema.Tool> tools = client.initialize()
                    .then(Mono.defer(client::listTools))
                    .block(initializationTimeout(configuration));
            if (tools == null) {
                throw new XException(ResponseCodeEnum.AGENT_MCP_CONNECTION_ERROR);
            }
            String endpoint = properties.runtimePath(userId, configuration.getId());
            HttpServletStreamableServerTransportProvider transportProvider = HttpServletStreamableServerTransportProvider.builder()
                    .mcpEndpoint(endpoint)
                    .build();
            McpAsyncServer server = io.modelcontextprotocol.server.McpServer.async(transportProvider)
                    .serverInfo(SERVER_NAME, SERVER_VERSION)
                    .requestTimeout(Duration.ofSeconds(configuration.getRequestTimeoutSeconds()))
                    .tools(toToolSpecifications(client, tools))
                    .build();
            return new McpProxyRuntime(client, transportProvider, server);
        } catch (RuntimeException error) {
            closeClient(client);
            throw error;
        }
    }

    /**
     * 将后端 MCP 工具定义转换为只负责转发调用的服务端工具定义。
     */
    private List<McpServerFeatures.AsyncToolSpecification> toToolSpecifications(
            McpClientWrapper client, List<McpSchema.Tool> tools) {
        return tools.stream()
                .map(tool -> McpServerFeatures.AsyncToolSpecification.builder()
                        .tool(tool)
                        .callHandler((exchange, request) -> callTool(client, request))
                        .build())
                .toList();
    }

    /**
     * 保留工具调用 metadata 并转发到后端 STDIO 或 Streamable HTTP MCP。
     */
    private Mono<McpSchema.CallToolResult> callTool(McpClientWrapper client, McpSchema.CallToolRequest request) {
        if (request.meta() == null || request.meta().isEmpty()) {
            return client.callTool(request.name(), request.arguments());
        }
        return client.callTool(request.name(), request.arguments(), request.meta());
    }

    /**
     * 给初始化和首次工具发现保留请求超时边界。
     */
    private Duration initializationTimeout(McpServer configuration) {
        long seconds = (long) configuration.getInitializationTimeoutSeconds()
                + configuration.getRequestTimeoutSeconds() + properties.getTimeoutBufferSeconds();
        return Duration.ofSeconds(seconds);
    }

    /**
     * 创建服务端失败时关闭已经启动的后端 MCP 客户端。
     */
    private void closeClient(McpClientWrapper client) {
        try {
            client.close();
        } catch (RuntimeException ignored) {
        }
    }
}
