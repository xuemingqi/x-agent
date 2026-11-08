package com.x.ai.service.impl;

import com.x.ai.common.model.AiModelRuntimeConfig;
import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.service.McpRuntimeService;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HarnessAgentMcpCancellationTest {

    private static final ResourceEnablement ALL_ENABLED = new ResourceEnablement(Set.of());

    @Mock
    private McpRuntimeService mcpRuntimeService;

    @InjectMocks
    private HarnessAgentServiceImpl service;

    @AfterEach
    void clearTestInterrupt() {
        Thread.interrupted();
    }

    @Test
    void interruptionDuringRegistrationClosesRegisteredFailedAndUnvisitedClients() {
        McpClientWrapper first = readyClient("first");
        McpClientWrapper interrupted = mock(McpClientWrapper.class);
        McpClientWrapper unvisited = mock(McpClientWrapper.class);
        RuntimeException failure = new IllegalStateException("tool discovery interrupted");
        when(interrupted.getName()).thenReturn("interrupted");
        when(interrupted.initialize()).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw failure;
        });
        when(mcpRuntimeService.getVisibleClients("42", ALL_ENABLED)).thenReturn(List.of(first, interrupted, unvisited));

        assertThatThrownBy(this::registerClients).isSameAs(failure);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(first).close();
        verify(interrupted).close();
        verify(unvisited).close();
        verify(unvisited, never()).initialize();
    }

    @Test
    void ordinaryRegistrationFailureClosesOnlyFailedClientAndRetainsSuccessfulClient() {
        McpClientWrapper first = readyClient("first");
        McpClientWrapper failed = mock(McpClientWrapper.class);
        when(failed.getName()).thenReturn("failed");
        when(failed.initialize()).thenReturn(Mono.error(new IllegalStateException("unavailable")));
        when(mcpRuntimeService.getVisibleClients("42", ALL_ENABLED)).thenReturn(List.of(first, failed));

        assertThat(registerClients()).containsExactly(first);

        verify(first, never()).close();
        verify(failed).close();
    }

    @Test
    void interruptionAfterClientsAreReturnedClosesThemWithoutStartingRegistration() {
        McpClientWrapper unvisited = mock(McpClientWrapper.class);
        when(mcpRuntimeService.getVisibleClients("42", ALL_ENABLED)).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return List.of(unvisited);
        });

        assertThatThrownBy(this::registerClients).isInstanceOf(CancellationException.class);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(unvisited).close();
        verify(unvisited, never()).initialize();
    }

    @Test
    void alreadyInterruptedRequestDoesNotLoadOrCacheAnAgent() {
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> service.getAgent(new AiModelRuntimeConfig(), "42"))
                .isInstanceOf(CancellationException.class);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verifyNoInteractions(mcpRuntimeService);
        assertThat((Map<?, ?>) ReflectionTestUtils.getField(service, "agentRuntimes")).isEmpty();
    }

    /**
     * 保留真实 Toolkit 注册流程，仅模拟 MCP 网络连接。
     */
    private McpClientWrapper readyClient(String name) {
        McpClientWrapper client = mock(McpClientWrapper.class);
        when(client.getName()).thenReturn(name);
        when(client.initialize()).thenReturn(Mono.empty());
        when(client.listTools()).thenReturn(Mono.just(List.of()));
        return client;
    }

    /**
     * 聚焦私有注册批次，避免测试无关模型、数据库和 Docker 初始化。
     */
    private List<McpClientWrapper> registerClients() {
        return ReflectionTestUtils.invokeMethod(service, "registerMcpClients", new Toolkit(), "42", ALL_ENABLED);
    }
}
