package com.x.ai.config;

import com.x.ai.service.ApiKeyService;
import com.x.ai.servlet.McpServerServlet;
import com.x.ai.tool.ExternalAgentMcpTool;
import com.x.framework.common.constants.CommonConstant;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

/**
 * 在 AI 服务内提供无状态 Streamable HTTP MCP，多节点无需维护协议会话亲和。
 */
@Configuration
public class McpServerConfiguration {

    private static final String ENDPOINT = "/mcp";

    @Bean
    public HttpServletStatelessServerTransport mcpServerTransport() {
        return HttpServletStatelessServerTransport.builder()
                .messageEndpoint(ENDPOINT)
                .contextExtractor(request -> McpTransportContext.create(Map.of(
                        CommonConstant.USER_ID, request.getAttribute(CommonConstant.USER_ID))))
                .build();
    }

    @Bean(destroyMethod = "close")
    public McpStatelessSyncServer externalMcpServer(HttpServletStatelessServerTransport transport,
                                                   ExternalAgentMcpTool tool) {
        return McpServer.sync(transport)
                .serverInfo("x-agent", "0.1.0")
                .instructions("使用 run_agent 工具调用 X Agent，sessionId 为空时自动创建会话。")
                .requestTimeout(Duration.ofMinutes(10))
                .tools(tool.specification())
                .build();
    }

    @Bean
    public ServletRegistrationBean<McpServerServlet> mcpServerServlet(ApiKeyService apiKeyService,
                                                                     HttpServletStatelessServerTransport transport,
                                                                     McpStatelessSyncServer server) {
        return new ServletRegistrationBean<>(new McpServerServlet(apiKeyService, transport), ENDPOINT);
    }
}
