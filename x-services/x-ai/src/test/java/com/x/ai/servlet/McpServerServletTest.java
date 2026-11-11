package com.x.ai.servlet;

import com.x.ai.common.enums.McpTransportType;
import com.x.ai.common.model.McpServer;
import com.x.ai.config.McpServerConfiguration;
import com.x.ai.service.ApiKeyService;
import com.x.ai.service.ChatService;
import com.x.ai.service.impl.McpClientServiceImpl;
import com.x.ai.tool.ExternalAgentMcpTool;
import com.x.api.ai.domain.ExternalAgentResponseDo;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.response.ResultUtil;
import com.x.framework.common.utils.JsonUtil;
import com.x.framework.common.utils.UserThreadLocalUtil;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Timeout(20)
class McpServerServletTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @TempDir
    private Path serverDirectory;

    private final ApiKeyService apiKeyService = mock(ApiKeyService.class);

    private final ChatService chatService = mock(ChatService.class);

    private final ValidatorFactory validators = Validation.buildDefaultValidatorFactory();

    private final List<WebServer> webServers = new ArrayList<>();

    private final List<McpStatelessSyncServer> protocolServers = new ArrayList<>();

    private final List<McpClientWrapper> clients = new ArrayList<>();

    private final AtomicReference<String> observedQueryCredential = new AtomicReference<>();

    @AfterEach
    void closeResources() {
        clients.forEach(McpClientWrapper::close);
        protocolServers.forEach(McpStatelessSyncServer::close);
        webServers.forEach(server -> {
            server.stop();
            server.destroy();
        });
        validators.close();
        UserThreadLocalUtil.clear();
    }

    @Test
    void rejectsMissingAndInvalidApiKeysBeforeProtocolHandling() throws Exception {
        when(apiKeyService.authenticateApiKey(null)).thenThrow(new XException(ResponseCodeEnum.API_KEY_INVALID));
        when(apiKeyService.authenticateApiKey("invalid")).thenThrow(new XException(ResponseCodeEnum.API_KEY_INVALID));
        String endpoint = startServer();

        assertThat(post(endpoint, null, "{}").statusCode()).isEqualTo(401);
        assertThat(post(endpoint, "invalid", "{}").statusCode()).isEqualTo(401);
        verifyNoInteractions(chatService);
    }

    @Test
    void connectsDirectlyOverHttpAndKeepsUserIdentityAndBusinessSession() throws Exception {
        when(apiKeyService.authenticateApiKey("first-key")).thenReturn(UserInfo.builder().userId("42").build());
        when(apiKeyService.authenticateApiKey("second-key")).thenReturn(UserInfo.builder().userId("43").build());
        when(chatService.runWithDefaultModel(any())).thenAnswer(invocation -> {
            com.x.api.ai.dto.ExternalAgentRequestDto request = invocation.getArgument(0);
            return ResultUtil.buildResultSuccess(new ExternalAgentResponseDo()
                    .setUserId(UserThreadLocalUtil.getUserId()).setSessionId(request.getSessionId())
                    .setText(request.getQuestion()));
        });
        String endpoint = startServer();
        McpClientWrapper first = connect(endpoint, "first-key");
        McpClientWrapper second = connect(endpoint, "second-key");

        assertThat(observedQueryCredential.get()).isEqualTo("secret +/值");
        assertThat(first.listTools().block(TIMEOUT)).extracting(McpSchema.Tool::name).containsExactly("run_agent");
        McpSchema.CallToolResult result = first.callTool("run_agent",
                Map.of("question", "hello", "sessionId", "existing-session")).block(TIMEOUT);
        assertThat(result.isError()).isFalse();
        var response = JsonUtil.readTree(((McpSchema.TextContent) result.content().getFirst()).text());
        assertThat(response.get("text").asText()).isEqualTo("hello");
        assertThat(response.get("userId").asText()).isEqualTo("42");
        assertThat(response.get("sessionId").asText()).isEqualTo("existing-session");

        McpSchema.CallToolResult secondResult = second.callTool("run_agent", Map.of("question", "other")).block(TIMEOUT);
        var secondResponse = JsonUtil.readTree(((McpSchema.TextContent) secondResult.content().getFirst()).text());
        assertThat(secondResponse.get("userId").asText()).isEqualTo("43");
        assertThat(UserThreadLocalUtil.get()).isNull();
    }

    @Test
    void acceptsProtocolRequestsOnAnotherNodeWithoutAnInitializationSession() throws Exception {
        when(apiKeyService.authenticateApiKey("valid")).thenReturn(UserInfo.builder().userId("42").build());
        String first = startServer();
        String second = startServer();
        String initialize = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
                """;

        HttpResponse<String> initialized = post(first, "valid", initialize);
        assertThat(initialized.statusCode()).isEqualTo(200);
        assertThat(initialized.headers().firstValue("Mcp-Session-Id")).isEmpty();
        HttpResponse<String> listed = post(second, "valid", """
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """);
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(JsonUtil.readTree(listed.body()).get("result").get("tools").get(0).get("name").asText())
                .isEqualTo("run_agent");
    }

    @Test
    void rejectsInvalidToolInputAndDoesNotExposeExecutionErrors() throws Exception {
        when(apiKeyService.authenticateApiKey("valid")).thenReturn(UserInfo.builder().userId("42").build());
        McpClientWrapper client = connect(startServer(), "valid");
        assertThat(client.callTool("run_agent", Map.of("question", "   ")).block(TIMEOUT).isError()).isTrue();
        verifyNoInteractions(chatService);

        when(chatService.runWithDefaultModel(any())).thenThrow(new IllegalStateException("private-token"));
        McpSchema.CallToolResult result = client.callTool("run_agent", Map.of("question", "hello")).block(TIMEOUT);
        assertThat(result.isError()).isTrue();
        assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).doesNotContain("private-token");
    }

    /**
     * 以真实 Servlet 容器验证 AI context path，不启动数据库、注册中心或模型。
     */
    private String startServer() throws Exception {
        McpServerConfiguration configuration = new McpServerConfiguration();
        var transport = configuration.mcpServerTransport();
        McpStatelessSyncServer protocolServer = configuration.externalMcpServer(transport,
                new ExternalAgentMcpTool(chatService, validators.getValidator()));
        protocolServers.add(protocolServer);
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory("/ai", 0);
        factory.setAddress(InetAddress.getByName("127.0.0.1"));
        factory.setBaseDirectory(serverDirectory.resolve("node-" + webServers.size()).toFile());
        WebServer webServer = factory.getWebServer(context -> {
            Filter observer = (request, response, chain) -> {
                observedQueryCredential.set(((HttpServletRequest) request).getParameter("access_token"));
                chain.doFilter(request, response);
            };
            context.addFilter("queryObserver", observer)
                    .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/mcp");
            configuration.mcpServerServlet(apiKeyService, transport, protocolServer).onStartup(context);
        });
        webServers.add(webServer);
        webServer.start();
        return "http://127.0.0.1:" + webServer.getPort() + "/ai/mcp";
    }

    /**
     * 使用生产 HTTP 客户端直接连接，无 Runtime 或 Gateway 服务参与。
     */
    private McpClientWrapper connect(String endpoint, String apiKey) {
        McpClientWrapper client = new McpClientServiceImpl().create(new McpServer().setName("test")
                .setTransportType(McpTransportType.STREAMABLE_HTTP).setEndpoint(endpoint)
                .setHeaders(Map.of("X-API-Key", apiKey)).setQueryParameters(Map.of("access_token", "secret +/值"))
                .setRequestTimeoutSeconds(5).setInitializationTimeoutSeconds(5));
        clients.add(client);
        client.initialize().then(Mono.defer(client::listTools)).block(TIMEOUT);
        return client;
    }

    /**
     * 独立发送 JSON-RPC 请求以模拟跨节点协议请求。
     */
    private HttpResponse<String> post(String endpoint, String apiKey, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(TIMEOUT)
                .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (apiKey != null) {
            request.header("X-API-Key", apiKey);
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
