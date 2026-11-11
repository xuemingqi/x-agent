package com.x.ai.service.impl;

import com.x.ai.common.enums.McpTransportType;
import com.x.ai.common.model.McpServer;
import com.x.ai.service.McpClientService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class McpClientServiceImpl implements McpClientService {

    @Override
    public McpClientWrapper create(McpServer server) {
        if (server == null || server.getTransportType() != McpTransportType.STREAMABLE_HTTP) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_TRANSPORT_UNSUPPORTED);
        }
        McpClientWrapper client = McpClientBuilder.create(server.getName())
                .streamableHttpTransport(server.getEndpoint())
                .headers(server.getHeaders())
                .queryParams(server.getQueryParameters())
                .timeout(Duration.ofSeconds(server.getRequestTimeoutSeconds()))
                .initializationTimeout(Duration.ofSeconds(server.getInitializationTimeoutSeconds()))
                .buildAsync()
                .block();
        if (client == null) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_CONNECTION_ERROR);
        }
        return client;
    }
}
