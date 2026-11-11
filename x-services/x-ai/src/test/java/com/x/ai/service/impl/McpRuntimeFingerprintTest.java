package com.x.ai.service.impl;

import com.x.ai.common.enums.McpTransportType;
import com.x.ai.common.model.McpServer;
import com.x.ai.service.McpClientService;
import com.x.ai.service.McpServerManagementService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpRuntimeFingerprintTest {

    @Test
    void credentialAndTimeoutChangesReplaceFingerprintWithoutExposingSecrets() {
        McpServerManagementService configurations = mock(McpServerManagementService.class);
        McpRuntimeServiceImpl runtime = new McpRuntimeServiceImpl(configurations, mock(McpClientService.class));
        McpServer server = new McpServer().setId(7L).setName("tools")
                .setTransportType(McpTransportType.STREAMABLE_HTTP).setEndpoint("https://tools.example/mcp")
                .setHeaders(Map.of("Authorization", "old-secret")).setEnabled(true)
                .setRequestTimeoutSeconds(30).setInitializationTimeoutSeconds(10);
        when(configurations.listRuntimeServers("42")).thenReturn(List.of(server));
        String original = runtime.load("42").getFingerprint();

        server.setHeaders(Map.of("Authorization", "new-secret"));
        String updatedCredential = runtime.load("42").getFingerprint();
        server.setRequestTimeoutSeconds(60);
        String updatedTimeout = runtime.load("42").getFingerprint();

        assertThat(original).hasSize(64).doesNotContain("old-secret");
        assertThat(updatedCredential).isNotEqualTo(original).doesNotContain("new-secret");
        assertThat(updatedTimeout).isNotEqualTo(updatedCredential);
    }
}
