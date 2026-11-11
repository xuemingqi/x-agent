package com.x.ai.service.impl;

import com.x.ai.common.model.McpServer;
import com.x.ai.common.enums.McpTransportType;
import com.x.ai.service.McpClientService;
import com.x.ai.service.McpServerManagementService;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CancellationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McpRuntimeCancellationTest {

    private final McpServerManagementService runtimeApi = mock(McpServerManagementService.class);

    private final McpClientService clientService = mock(McpClientService.class);

    private final McpRuntimeServiceImpl service = new McpRuntimeServiceImpl(runtimeApi, clientService);

    @AfterEach
    void clearTestInterrupt() {
        Thread.interrupted();
    }

    @Test
    void interruptedRequestNeverStartsDiscovery() {
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> service.getClients(service.load("42")))
                .isInstanceOf(CancellationException.class);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verifyNoInteractions(runtimeApi, clientService);
    }

    @Test
    void interruptedInitializationClosesEarlierClientsAndPreservesOriginalFailure() {
        McpClientWrapper first = mock(McpClientWrapper.class);
        McpClientWrapper second = mock(McpClientWrapper.class);
        RuntimeException interruption = new IllegalStateException("MCP initialization interrupted");
        when(runtimeApi.listRuntimeServers("42")).thenReturn(List.of(
                runtimeServer(1L), runtimeServer(2L), runtimeServer(3L), runtimeServer(4L)));
        doThrow(new IllegalStateException("close failed")).when(first).close();

        when(clientService.create(any())).thenReturn(first, second).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw interruption;
        });

        assertThatThrownBy(() -> service.getClients(service.load("42"))).isSameAs(interruption);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(first).close();
        verify(second).close();
        verify(clientService, times(3)).create(any());
    }

    @Test
    void cancellationAtLastSuccessfulInitializationStillClosesTheUnclaimedClient() {
        McpClientWrapper client = mock(McpClientWrapper.class);
        when(runtimeApi.listRuntimeServers("42")).thenReturn(List.of(runtimeServer(1L)));

        when(clientService.create(any())).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return client;
        });

        assertThatThrownBy(() -> service.getClients(service.load("42")))
                .isInstanceOf(CancellationException.class);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(client).close();
    }

    @Test
    void ordinaryServerFailureStillSkipsOnlyThatServerAndKeepsSuccessfulClientsOpen() {
        McpClientWrapper client = mock(McpClientWrapper.class);
        when(runtimeApi.listRuntimeServers("42")).thenReturn(List.of(runtimeServer(1L), runtimeServer(2L)));

        when(clientService.create(any())).thenThrow(new IllegalStateException("unavailable")).thenReturn(client);

        assertThat(service.getClients(service.load("42"))).containsExactly(client);

        verify(client, never()).close();
        verify(clientService, times(2)).create(any());
    }

    @Test
    void creatingClientsDoesNotReadConfigurationAgain() {
        McpServer configuration = runtimeServer(1L);
        McpClientWrapper client = mock(McpClientWrapper.class);
        when(runtimeApi.listRuntimeServers("42")).thenReturn(List.of(configuration));
        when(clientService.create(configuration)).thenReturn(client);
        var snapshot = service.load("42");

        assertThat(service.getClients(snapshot)).containsExactly(client);

        verify(runtimeApi, times(1)).listRuntimeServers("42");
        verify(clientService).create(configuration);
        verify(client, never()).close();
    }

    /**
     * 使用独立名称使每个测试连接都参与当前初始化批次。
     */
    private McpServer runtimeServer(Long id) {
        return new McpServer().setId(id).setName("server-" + id).setTransportType(McpTransportType.STREAMABLE_HTTP)
                .setEndpoint("https://mcp.example/" + id)
                .setRequestTimeoutSeconds(30).setInitializationTimeoutSeconds(10);
    }
}
