package com.x.ai.mcp.runtime;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.x.ai.mcp.api.enums.McpScope;
import com.x.ai.mcp.config.McpRuntimeProperties;
import com.x.ai.mcp.config.McpServletConfiguration;
import com.x.ai.mcp.db.entity.AgentMcpServer;
import com.x.ai.mcp.db.service.AgentMcpServerIService;
import com.x.ai.mcp.model.McpServer;
import com.x.ai.mcp.service.McpClientService;
import com.x.ai.mcp.service.McpUserCredentialService;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpRuntimeServletTest {

    private static final Long CONFIGURATION_ID = 42L;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    @TempDir
    private Path serverDirectory;

    private final AgentMcpServerIService serverService = mock(AgentMcpServerIService.class);

    private final McpUserCredentialService credentialService = mock(McpUserCredentialService.class);

    private final McpClientService clientService = mock(McpClientService.class);

    private final McpRuntimeProperties properties = new McpRuntimeProperties();

    private final List<McpSyncClient> clients = new ArrayList<>();

    private McpRuntimeRegistry registry;

    private WebServer webServer;

    @AfterEach
    void closeServersAndClients() {
        clients.forEach(McpSyncClient::closeGracefully);
        if (registry != null) {
            registry.close();
        }
        if (webServer != null) {
            webServer.stop();
            webServer.destroy();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/mcp", ""})
    @Timeout(20)
    void initializesAndCallsUserIsolatedToolsThroughAdvertisedServletPath(String contextPath) throws Exception {
        AgentMcpServer server = new AgentMcpServer()
                .setId(CONFIGURATION_ID)
                .setName("shared-tools")
                .setScope(McpScope.PUBLIC)
                .setRequestTimeoutSeconds(3)
                .setInitializationTimeoutSeconds(3);
        LambdaQueryChainWrapper<AgentMcpServer> query = mock(invocation ->
                switch (invocation.getMethod().getName()) {
                    case "eq", "orderByAsc" -> invocation.getMock();
                    default -> RETURNS_DEFAULTS.answer(invocation);
                });
        when(serverService.visibleQuery(anyString())).thenReturn(query);
        when(query.list()).thenReturn(List.of(server));
        when(query.one()).thenReturn(server);
        when(credentialService.configuredServerIds(anyString(), anyList())).thenReturn(Set.of());

        String firstUser = "user+%40 name@example.com";
        String secondUser = "另一用户@example.com";
        McpClientWrapper firstBackend = backendFor(firstUser, server);
        McpClientWrapper secondBackend = backendFor(secondUser, server);
        startServer(contextPath);

        String firstPath = registry.list(firstUser).getFirst().getRuntimePath();
        String secondPath = registry.list(secondUser).getFirst().getRuntimePath();
        assertThat(firstPath).isEqualTo(contextPath + "/internal/mcp/user%2B%2540%20name%40example.com/42");
        assertThat(secondPath).isEqualTo(contextPath + properties.runtimePath(secondUser, CONFIGURATION_ID));
        McpSyncClient firstClient = connect(firstPath);
        McpSyncClient secondClient = connect(secondPath);

        assertThat(firstClient.getServerInfo().name()).isEqualTo("x-ai-mcp-runtime");
        assertThat(firstClient.listTools().tools()).extracting(McpSchema.Tool::name).containsExactly("owner");
        assertThat(firstClient.callTool(new McpSchema.CallToolRequest("owner", Map.of())).content())
                .containsExactly(new McpSchema.TextContent(firstUser));
        assertThat(secondClient.callTool(new McpSchema.CallToolRequest("owner", Map.of())).content())
                .containsExactly(new McpSchema.TextContent(secondUser));
        verify(firstBackend).initialize();
        verify(secondBackend).initialize();
        verify(firstBackend).callTool("owner", Map.of());
        verify(secondBackend).callTool("owner", Map.of());
        verify(serverService, atLeastOnce()).visibleQuery(firstUser);
        verify(serverService, atLeastOnce()).visibleQuery(secondUser);

        if (!contextPath.isEmpty()) {
            try (HttpClient httpClient = HttpClient.newHttpClient()) {
                HttpRequest duplicatedContextRequest = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl() + contextPath + firstPath))
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();
                assertThat(httpClient.send(duplicatedContextRequest, HttpResponse.BodyHandlers.discarding()).statusCode())
                        .isEqualTo(404);
            }
        }
    }

    /**
     * 使用真实 Servlet 容器，外部凭证、数据库和 MCP 后端保持为测试替身。
     */
    private void startServer(String contextPath) throws Exception {
        properties.setProxyEndpoint("/internal/mcp/");
        McpRuntimeFactory runtimeFactory = new McpRuntimeFactory(clientService, properties);
        TomcatServletWebServerFactory serverFactory = new TomcatServletWebServerFactory(contextPath, 0);
        serverFactory.setAddress(InetAddress.getByName("127.0.0.1"));
        serverFactory.setBaseDirectory(serverDirectory.toFile());
        webServer = serverFactory.getWebServer(servletContext -> {
            registry = new McpRuntimeRegistry(serverService, credentialService, runtimeFactory, properties, servletContext);
            new McpServletConfiguration().mcpProxyServlet(registry, properties).onStartup(servletContext);
        });
        webServer.start();
    }

    /**
     * 不同用户的同一公共配置使用独立后端，以检测路径解码或运行实例串用。
     */
    private McpClientWrapper backendFor(String userId, AgentMcpServer server) {
        McpServer configuration = new McpServer()
                .setId(CONFIGURATION_ID)
                .setName(userId)
                .setRequestTimeoutSeconds(3)
                .setInitializationTimeoutSeconds(3);
        McpClientWrapper backend = mock(McpClientWrapper.class);
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("owner")
                .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null))
                .build();
        when(credentialService.toRuntimeServer(userId, server)).thenReturn(configuration);
        when(clientService.create(configuration)).thenReturn(backend);
        when(backend.initialize()).thenReturn(Mono.empty());
        when(backend.listTools()).thenReturn(Mono.just(List.of(tool)));
        when(backend.callTool("owner", Map.of()))
                .thenReturn(Mono.just(McpSchema.CallToolResult.builder().addTextContent(userId).build()));
        return backend;
    }

    /**
     * 通过 SDK 完成真正的 HTTP 初始化握手，而不是只比较拼接后的路径。
     */
    private McpSyncClient connect(String endpoint) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(baseUrl())
                .endpoint(endpoint)
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(REQUEST_TIMEOUT)
                .initializationTimeout(REQUEST_TIMEOUT)
                .build();
        clients.add(client);
        client.initialize();
        return client;
    }

    /**
     * 测试仅监听本机随机端口，不依赖环境中的服务发现配置。
     */
    private String baseUrl() {
        return "http://127.0.0.1:" + webServer.getPort();
    }
}
