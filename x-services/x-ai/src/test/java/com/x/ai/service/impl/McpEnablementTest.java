package com.x.ai.service.impl;

import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.mcp.api.McpRuntimeApi;
import com.x.ai.mcp.api.domain.McpRuntimeServerDo;
import com.x.ai.mcp.api.enums.McpScope;
import com.x.ai.service.HarnessAgentService;
import com.x.ai.service.McpConnectionService;
import com.x.ai.service.ResourceEnablementService;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.UserThreadLocalUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McpEnablementTest {

    @AfterEach
    void clearIdentity() {
        UserThreadLocalUtil.clear();
    }

    @Test
    void disabledMcpNeverCreatesRuntimeConnection() {
        McpRuntimeApi runtime = mock(McpRuntimeApi.class);
        LoadBalancerClient loadBalancer = mock(LoadBalancerClient.class);
        when(runtime.listRuntimeServers("42")).thenReturn(List.of(new McpRuntimeServerDo().setId(7L).setName("disabled")));
        McpRuntimeServiceImpl service = new McpRuntimeServiceImpl(runtime, loadBalancer);

        assertTrue(service.getVisibleClients("42", new ResourceEnablement(Set.of(ResourceEnablement.mcpKey(7L)))).isEmpty());

        verifyNoInteractions(loadBalancer);
    }

    @Test
    void inaccessibleMcpCannotCreateEnablementPreference() {
        McpRuntimeApi runtime = mock(McpRuntimeApi.class);
        ResourceEnablementService enablement = mock(ResourceEnablementService.class);
        McpServerServiceImpl service = new McpServerServiceImpl(runtime, mock(HarnessAgentService.class),
                mock(McpConnectionService.class), enablement);
        UserThreadLocalUtil.set(UserInfo.builder().userId("42").build());
        when(runtime.get("42", McpScope.USER.getCode(), 7L))
                .thenThrow(new XException(ResponseCodeEnum.AGENT_MCP_NOT_FOUND));

        assertThrows(XException.class, () -> service.setEnabled(McpScope.USER, 7L, false));

        verifyNoInteractions(enablement);
    }
}
