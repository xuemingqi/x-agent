package com.x.ai.service.impl;

import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.mcp.api.McpRuntimeApi;
import com.x.ai.mcp.api.domain.McpRuntimeServerDo;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class McpRuntimeDiscoveryTest {

    private static final String USER_ID = "42";

    private static final ResourceEnablement ALL_ENABLED = new ResourceEnablement(Set.of());

    private final McpRuntimeApi runtimeApi = mock(McpRuntimeApi.class);

    private final LoadBalancerClient loadBalancerClient = mock(LoadBalancerClient.class);

    private final McpClientBuilder builder = mock(McpClientBuilder.class, RETURNS_SELF);

    private final McpClientWrapper client = mock(McpClientWrapper.class);

    private final McpRuntimeServiceImpl service = new McpRuntimeServiceImpl(runtimeApi, loadBalancerClient);

    @Test
    void discoversHostAndPortWithoutEncodingRuntimePathTwice() {
        McpRuntimeServerDo server = runtimeServer(7L, "search")
                .setRuntimePath("/mcp/internal/mcp/user%40example.com/7");
        when(runtimeApi.listRuntimeServers(USER_ID)).thenReturn(List.of(server));
        when(loadBalancerClient.choose(McpRuntimeApi.SERVICE_NAME)).thenReturn(new DefaultServiceInstance(
                "runtime-1", McpRuntimeApi.SERVICE_NAME, "192.0.2.17", 28087, false));

        try (MockedStatic<McpClientBuilder> builders = mockClientBuilder()) {
            List<McpClientWrapper> clients = service.getVisibleClients(USER_ID, ALL_ENABLED);

            assertEquals(List.of(client), clients);
            builders.verify(() -> McpClientBuilder.create("search"));
            verify(builder).streamableHttpTransport("http://192.0.2.17:28087/mcp/internal/mcp/user%40example.com/7");
            verify(builder).timeout(Duration.ofSeconds(31));
            verify(builder).initializationTimeout(Duration.ofSeconds(42));
            verify(loadBalancerClient).choose(McpRuntimeApi.SERVICE_NAME);
            verifyNoMoreInteractions(loadBalancerClient);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "runtime.example.test, true, https://runtime.example.test:18443/mcp/internal/mcp/42/7",
            "2001:db8::17, true, https://[2001:db8::17]:18443/mcp/internal/mcp/42/7"
    })
    void preservesSecureSchemeAndIpv6Address(String host, boolean secure, String expectedEndpoint) {
        when(runtimeApi.listRuntimeServers(USER_ID)).thenReturn(List.of(runtimeServer(7L, "search")));
        when(loadBalancerClient.choose(McpRuntimeApi.SERVICE_NAME)).thenReturn(new DefaultServiceInstance(
                "runtime-1", McpRuntimeApi.SERVICE_NAME, host, 18443, secure));

        try (MockedStatic<McpClientBuilder> builders = mockClientBuilder()) {
            assertEquals(List.of(client), service.getVisibleClients(USER_ID, ALL_ENABLED));

            builders.verify(() -> McpClientBuilder.create("search"));
            verify(builder).streamableHttpTransport(expectedEndpoint);
        }
    }

    @Test
    void skipsUnavailableServiceBeforeBuildingMcpConnection() {
        when(runtimeApi.listRuntimeServers(USER_ID)).thenReturn(List.of(runtimeServer(7L, "search")));

        try (MockedStatic<McpClientBuilder> builders = mockClientBuilder()) {
            assertTrue(service.getVisibleClients(USER_ID, ALL_ENABLED).isEmpty());

            verify(loadBalancerClient).choose(McpRuntimeApi.SERVICE_NAME);
            builders.verifyNoInteractions();
            verifyNoInteractions(builder, client);
        }
    }

    @Test
    void skipsDisabledResourceBeforeDiscoveringRuntimeInstance() {
        when(runtimeApi.listRuntimeServers(USER_ID)).thenReturn(List.of(runtimeServer(7L, "search")));
        ResourceEnablement disabled = new ResourceEnablement(Set.of(ResourceEnablement.mcpKey(7L)));

        try (MockedStatic<McpClientBuilder> builders = mockClientBuilder()) {
            assertTrue(service.getVisibleClients(USER_ID, disabled).isEmpty());

            builders.verifyNoInteractions();
            verifyNoInteractions(loadBalancerClient, builder, client);
        }
    }

    @Test
    void skipsDuplicateNamespaceWithoutDiscoveringAnotherInstance() {
        when(runtimeApi.listRuntimeServers(USER_ID)).thenReturn(List.of(
                runtimeServer(7L, "search"), runtimeServer(8L, "search")));
        when(loadBalancerClient.choose(McpRuntimeApi.SERVICE_NAME)).thenReturn(new DefaultServiceInstance(
                "runtime-1", McpRuntimeApi.SERVICE_NAME, "192.0.2.17", 18087, false));

        try (MockedStatic<McpClientBuilder> builders = mockClientBuilder()) {
            assertEquals(List.of(client), service.getVisibleClients(USER_ID, ALL_ENABLED));

            builders.verify(() -> McpClientBuilder.create("search"));
            builders.verifyNoMoreInteractions();
            verify(loadBalancerClient).choose(McpRuntimeApi.SERVICE_NAME);
            verifyNoMoreInteractions(loadBalancerClient);
        }
    }

    @Test
    void keepsExistingClientOnChosenInstanceDuringToolCalls() {
        when(runtimeApi.listRuntimeServers(USER_ID)).thenReturn(List.of(runtimeServer(7L, "search")));
        when(loadBalancerClient.choose(McpRuntimeApi.SERVICE_NAME)).thenReturn(new DefaultServiceInstance(
                "runtime-1", McpRuntimeApi.SERVICE_NAME, "192.0.2.17", 18087, false));
        when(client.callTool("search", Map.of("query", "first"))).thenReturn(Mono.empty());
        when(client.callTool("search", Map.of("query", "second"))).thenReturn(Mono.empty());

        try (MockedStatic<McpClientBuilder> builders = mockClientBuilder()) {
            McpClientWrapper visibleClient = service.getVisibleClients(USER_ID, ALL_ENABLED).getFirst();
            visibleClient.callTool("search", Map.of("query", "first")).block();
            visibleClient.callTool("search", Map.of("query", "second")).block();

            assertSame(client, visibleClient);
            verify(client).callTool("search", Map.of("query", "first"));
            verify(client).callTool("search", Map.of("query", "second"));
            builders.verify(() -> McpClientBuilder.create("search"));
            verify(loadBalancerClient).choose(McpRuntimeApi.SERVICE_NAME);
            verifyNoMoreInteractions(loadBalancerClient);
        }
    }

    @Test
    void discoversCurrentInstanceAgainWhenCreatingAnotherClient() {
        when(runtimeApi.listRuntimeServers(USER_ID)).thenReturn(List.of(runtimeServer(7L, "search")));
        when(loadBalancerClient.choose(McpRuntimeApi.SERVICE_NAME)).thenReturn(
                new DefaultServiceInstance("runtime-1", McpRuntimeApi.SERVICE_NAME, "192.0.2.17", 18087, false),
                new DefaultServiceInstance("runtime-2", McpRuntimeApi.SERVICE_NAME, "192.0.2.18", 28087, false));
        McpClientWrapper nextClient = mock(McpClientWrapper.class);

        try (MockedStatic<McpClientBuilder> builders = mockClientBuilder()) {
            when(builder.buildAsync()).thenReturn(Mono.just(client), Mono.just(nextClient));

            assertEquals(List.of(client), service.getVisibleClients(USER_ID, ALL_ENABLED));
            assertEquals(List.of(nextClient), service.getVisibleClients(USER_ID, ALL_ENABLED));

            builders.verify(() -> McpClientBuilder.create("search"), times(2));
            ArgumentCaptor<String> endpoints = ArgumentCaptor.forClass(String.class);
            verify(builder, times(2)).streamableHttpTransport(endpoints.capture());
            assertEquals(List.of("http://192.0.2.17:18087/mcp/internal/mcp/42/7",
                    "http://192.0.2.18:28087/mcp/internal/mcp/42/7"), endpoints.getAllValues());
            verify(loadBalancerClient, times(2)).choose(McpRuntimeApi.SERVICE_NAME);
            verifyNoMoreInteractions(loadBalancerClient);
        }
    }

    /**
     * 替换网络客户端构建边界，地址重建和启停过滤仍执行真实业务逻辑。
     */
    private MockedStatic<McpClientBuilder> mockClientBuilder() {
        MockedStatic<McpClientBuilder> builders = mockStatic(McpClientBuilder.class);
        builders.when(() -> McpClientBuilder.create(anyString())).thenReturn(builder);
        when(builder.buildAsync()).thenReturn(Mono.just(client));
        return builders;
    }

    /**
     * 提供与 Runtime 返回契约一致的运行路径和超时描述。
     */
    private McpRuntimeServerDo runtimeServer(Long id, String name) {
        return new McpRuntimeServerDo().setId(id).setName(name)
                .setRuntimePath("/mcp/internal/mcp/" + USER_ID + "/" + id)
                .setRequestTimeoutSeconds(30).setInitializationTimeoutSeconds(10);
    }
}
