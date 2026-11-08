package com.x.ai.service.impl;

import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.mcp.api.McpRuntimeApi;
import com.x.ai.mcp.api.domain.McpRuntimeServerDo;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McpRuntimeCancellationTest {

    private static final ResourceEnablement ALL_ENABLED = new ResourceEnablement(Set.of());

    private final McpRuntimeApi runtimeApi = mock(McpRuntimeApi.class);

    private final LoadBalancerClient loadBalancerClient = mock(LoadBalancerClient.class);

    private final McpClientBuilder builder = mock(McpClientBuilder.class, RETURNS_SELF);

    private final McpRuntimeServiceImpl service = new McpRuntimeServiceImpl(runtimeApi, loadBalancerClient);

    @AfterEach
    void clearTestInterrupt() {
        Thread.interrupted();
    }

    @Test
    void interruptedRequestNeverStartsDiscovery() {
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> service.getVisibleClients("42", ALL_ENABLED))
                .isInstanceOf(CancellationException.class);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verifyNoInteractions(runtimeApi, loadBalancerClient);
    }

    @Test
    void interruptedInitializationClosesEarlierClientsAndPreservesOriginalFailure() {
        McpClientWrapper first = mock(McpClientWrapper.class);
        McpClientWrapper second = mock(McpClientWrapper.class);
        RuntimeException interruption = new IllegalStateException("MCP initialization interrupted");
        when(runtimeApi.listRuntimeServers("42")).thenReturn(List.of(
                runtimeServer(1L), runtimeServer(2L), runtimeServer(3L), runtimeServer(4L)));
        doThrow(new IllegalStateException("close failed")).when(first).close();

        try (MockedStatic<McpClientBuilder> ignored = mockClientBuilder()) {
            when(builder.buildAsync()).thenReturn(Mono.just(first), Mono.just(second)).thenAnswer(invocation -> {
                Thread.currentThread().interrupt();
                throw interruption;
            });

            assertThatThrownBy(() -> service.getVisibleClients("42", ALL_ENABLED)).isSameAs(interruption);

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(first).close();
            verify(second).close();
            verify(builder, times(3)).buildAsync();
        }
    }

    @Test
    void cancellationAtLastSuccessfulInitializationStillClosesTheUnclaimedClient() {
        McpClientWrapper client = mock(McpClientWrapper.class);
        when(runtimeApi.listRuntimeServers("42")).thenReturn(List.of(runtimeServer(1L)));

        try (MockedStatic<McpClientBuilder> ignored = mockClientBuilder()) {
            when(builder.buildAsync()).thenAnswer(invocation -> {
                Thread.currentThread().interrupt();
                return Mono.just(client);
            });

            assertThatThrownBy(() -> service.getVisibleClients("42", ALL_ENABLED))
                    .isInstanceOf(CancellationException.class);

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(client).close();
        }
    }

    @Test
    void ordinaryServerFailureStillSkipsOnlyThatServerAndKeepsSuccessfulClientsOpen() {
        McpClientWrapper client = mock(McpClientWrapper.class);
        when(runtimeApi.listRuntimeServers("42")).thenReturn(List.of(runtimeServer(1L), runtimeServer(2L)));

        try (MockedStatic<McpClientBuilder> ignored = mockClientBuilder()) {
            when(builder.buildAsync()).thenReturn(Mono.error(new IllegalStateException("unavailable")), Mono.just(client));

            assertThat(service.getVisibleClients("42", ALL_ENABLED)).containsExactly(client);

            verify(client, never()).close();
            verify(builder, times(2)).buildAsync();
        }
    }

    /**
     * 仅替换客户端网络边界，执行真实发现和批次生命周期。
     */
    private MockedStatic<McpClientBuilder> mockClientBuilder() {
        when(loadBalancerClient.choose(McpRuntimeApi.SERVICE_NAME)).thenReturn(new DefaultServiceInstance(
                "runtime-1", McpRuntimeApi.SERVICE_NAME, "192.0.2.17", 18087, false));
        MockedStatic<McpClientBuilder> builders = mockStatic(McpClientBuilder.class);
        builders.when(() -> McpClientBuilder.create(anyString())).thenReturn(builder);
        return builders;
    }

    /**
     * 使用独立名称使每个测试连接都参与当前初始化批次。
     */
    private McpRuntimeServerDo runtimeServer(Long id) {
        return new McpRuntimeServerDo().setId(id).setName("server-" + id)
                .setRuntimePath("/mcp/internal/mcp/42/" + id)
                .setRequestTimeoutSeconds(30).setInitializationTimeoutSeconds(10);
    }
}
