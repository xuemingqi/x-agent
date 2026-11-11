package com.x.ai.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.x.ai.common.domain.McpServerDo;
import com.x.ai.common.dto.McpServerCreateDto;
import com.x.ai.common.dto.McpServerUpdateDto;
import com.x.ai.common.enums.McpTransportType;
import com.x.ai.common.model.McpServer;
import com.x.ai.converter.McpServerConverter;
import com.x.ai.db.entity.AgentMcpServer;
import com.x.ai.db.service.AgentMcpServerIService;
import com.x.ai.service.McpClientService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.JsonUtil;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpServerManagementServiceImplTest {

    private final AgentMcpServerIService databaseService = mock(AgentMcpServerIService.class);

    private final McpClientService clientService = mock(McpClientService.class);

    private final McpServerManagementServiceImpl service = new McpServerManagementServiceImpl(databaseService,
            new Snowflake(1, 1), new McpServerConverter(), clientService);

    @Test
    void repositoryQueryAlwaysIncludesRequestedOwner() {
        LambdaQueryChainWrapper<AgentMcpServer> query = query();
        when(databaseService.lambdaQuery()).thenReturn(query);
        when(databaseService.ownedQuery("42")).thenCallRealMethod();
        when(query.list()).thenReturn(List.of());

        service.list("42");

        verify(query).eq(any(), org.mockito.ArgumentMatchers.eq("42"));
    }

    @Test
    void createsOwnedHttpConfigurationWithoutExposingCredentials() throws Exception {
        LambdaQueryChainWrapper<AgentMcpServer> query = query();
        when(databaseService.ownedQuery("42")).thenReturn(query);
        when(databaseService.save(any())).thenReturn(true);

        McpServerDo result = service.create("42", new McpServerCreateDto().setName("tools")
                .setDescription("工具服务").setTransportType(McpTransportType.STREAMABLE_HTTP)
                .setEndpoint("https://tools.example/mcp?token=a%2Bb%3D")
                .setHeaders(Map.of("Authorization", "private-token"))
                .setEnabled(true).setRequestTimeoutSeconds(30).setInitializationTimeoutSeconds(10));

        ArgumentCaptor<AgentMcpServer> saved = ArgumentCaptor.forClass(AgentMcpServer.class);
        verify(databaseService).save(saved.capture());
        assertThat(saved.getValue().getOwnerUserId()).isEqualTo("42");
        assertThat(saved.getValue().getEndpoint()).isEqualTo("https://tools.example/mcp");
        assertThat(JsonUtil.readTree(saved.getValue().getQueryParametersJson()).get("token").asText())
                .isEqualTo("a+b=");
        assertThat(result.getHeaders()).containsExactlyEntriesOf(Map.of("Authorization", ""));
        assertThat(result.getQueryParameters()).containsExactlyEntriesOf(Map.of("token", ""));
        assertThat(result.getEndpoint()).doesNotContain("token");
    }

    @Test
    void editingMaskedCredentialsPreservesSubmittedKeysAndDropsRemovedKeys() throws Exception {
        AgentMcpServer existing = server(7L)
                .setHeadersJson("{\"Authorization\":\"secret\",\"removed\":\"old\"}")
                .setQueryParametersJson("{\"token\":\"query-secret\"}");
        LambdaQueryChainWrapper<AgentMcpServer> query = query();
        when(databaseService.ownedQuery("42")).thenReturn(query);
        when(query.one()).thenReturn(existing);
        when(databaseService.updateById(existing)).thenReturn(true);

        service.update("42", 7L, new McpServerUpdateDto().setDescription("更新后的工具")
                .setTransportType(McpTransportType.STREAMABLE_HTTP).setEndpoint("https://tools.example/mcp")
                .setHeaders(Map.of("authorization", "")).setQueryParameters(Map.of("token", ""))
                .setEnabled(true).setRequestTimeoutSeconds(30).setInitializationTimeoutSeconds(10));

        assertThat(JsonUtil.readTree(existing.getHeadersJson()).get("authorization").asText()).isEqualTo("secret");
        assertThat(JsonUtil.readTree(existing.getHeadersJson()).has("removed")).isFalse();
        assertThat(JsonUtil.readTree(existing.getQueryParametersJson()).get("token").asText()).isEqualTo("query-secret");
    }

    @Test
    void missingOwnedConfigurationCannotBeUpdated() {
        when(databaseService.ownedQuery("42")).thenReturn(query());

        assertThatThrownBy(() -> service.update("42", 7L, new McpServerUpdateDto()))
                .isInstanceOf(XException.class)
                .extracting(error -> ((XException) error).getResponseCodeEnum())
                .isEqualTo(ResponseCodeEnum.AGENT_MCP_NOT_FOUND);

        verify(databaseService, never()).updateById(any(AgentMcpServer.class));
    }

    @Test
    void runtimeListUsesOwnedEnabledConfiguration() {
        AgentMcpServer configuration = server(7L).setHeadersJson("{\"Authorization\":\"private-token\"}");
        LambdaQueryChainWrapper<AgentMcpServer> query = query();
        when(databaseService.ownedQuery("42")).thenReturn(query);
        when(query.list()).thenReturn(List.of(configuration));

        var runtime = service.listRuntimeServers("42").getFirst();

        assertThat(runtime.getHeaders()).containsEntry("Authorization", "private-token");
        verify(query).eq(any(), org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void inaccessibleConfigurationCannotBeReadTestedOrDeleted() {
        when(databaseService.ownedQuery("other-user")).thenReturn(query());

        assertThatThrownBy(() -> service.get("other-user", 7L)).isInstanceOf(XException.class);
        assertThatThrownBy(() -> service.testConnection("other-user", 7L)).isInstanceOf(XException.class);
        assertThatThrownBy(() -> service.delete("other-user", 7L)).isInstanceOf(XException.class);
        verify(databaseService, never()).removeById(any(Long.class));
        org.mockito.Mockito.verifyNoInteractions(clientService);
    }

    @Test
    void connectionTestDiscoversToolsAndAlwaysClosesItsTemporaryClient() {
        McpClientWrapper client = temporaryClient();
        when(client.initialize()).thenReturn(Mono.empty());
        when(client.listTools()).thenReturn(Mono.just(List.of(
                McpSchema.Tool.builder().name("zeta").build(), McpSchema.Tool.builder().name("alpha").build())));

        assertThat(service.testConnection("42", 7L).getToolNames()).containsExactly("alpha", "zeta");

        verify(client).close();
    }

    @Test
    void failedConnectionClosesTemporaryClientAndReturnsStableBusinessError() {
        McpClientWrapper client = temporaryClient();
        when(client.initialize()).thenReturn(Mono.error(new IllegalStateException("secret-token")));

        assertThatThrownBy(() -> service.testConnection("42", 7L))
                .isInstanceOf(XException.class)
                .hasMessageNotContaining("secret-token")
                .extracting(error -> ((XException) error).getResponseCodeEnum())
                .isEqualTo(ResponseCodeEnum.AGENT_MCP_CONNECTION_ERROR);

        verify(client).close();
        verify(client, never()).listTools();
    }

    /**
     * 替换数据库查询边界，保留配置、凭据和客户端生命周期的真实编排。
     */
    private McpClientWrapper temporaryClient() {
        AgentMcpServer server = server(7L);
        LambdaQueryChainWrapper<AgentMcpServer> query = query();
        when(databaseService.ownedQuery("42")).thenReturn(query);
        when(query.one()).thenReturn(server);
        McpClientWrapper client = mock(McpClientWrapper.class);
        when(clientService.create(any(McpServer.class))).thenReturn(client);
        return client;
    }

    /**
     * 使用真实领域对象记录待保存的 HTTP 配置。
     */
    private AgentMcpServer server(Long id) {
        return new AgentMcpServer().setId(id).setName("tools-" + id).setOwnerUserId("42")
                .setTransportType(McpTransportType.STREAMABLE_HTTP).setEndpoint("https://tools.example/mcp")
                .setHeadersJson("{}").setQueryParametersJson("{}")
                .setEnabled(true).setRequestTimeoutSeconds(30).setInitializationTimeoutSeconds(10);
    }

    /**
     * 链式条件仍由服务组装，终端操作由测试控制。
     */
    @SuppressWarnings("unchecked")
    private LambdaQueryChainWrapper<AgentMcpServer> query() {
        return mock(LambdaQueryChainWrapper.class, invocation -> switch (invocation.getMethod().getName()) {
            case "eq", "in", "orderByAsc" -> invocation.getMock();
            default -> RETURNS_DEFAULTS.answer(invocation);
        });
    }
}
