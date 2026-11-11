package com.x.ai.service.impl;

import com.x.ai.service.HarnessAgentService;
import com.x.ai.service.McpClientService;
import com.x.ai.service.McpConnectionService;
import com.x.ai.service.McpServerManagementService;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.UserThreadLocalUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McpEnablementTest {

    @AfterEach
    void clearIdentity() {
        UserThreadLocalUtil.clear();
    }

    @Test
    void emptyOwnedRuntimeListCreatesNoConnections() {
        McpServerManagementService configuration = mock(McpServerManagementService.class);
        McpClientService clients = mock(McpClientService.class);
        when(configuration.listRuntimeServers("42")).thenReturn(List.of());
        McpRuntimeServiceImpl service = new McpRuntimeServiceImpl(configuration, clients);

        assertTrue(service.getClients(service.load("42")).isEmpty());
        verifyNoInteractions(clients);
    }

    @Test
    void inaccessibleMcpCannotChangeEnablementOrEvictRuntime() {
        McpServerManagementService configuration = mock(McpServerManagementService.class);
        HarnessAgentService agents = mock(HarnessAgentService.class);
        McpServerServiceImpl service = new McpServerServiceImpl(configuration, agents,
                mock(McpConnectionService.class));
        UserThreadLocalUtil.set(UserInfo.builder().userId("42").build());
        when(configuration.setEnabled("42", 7L, false))
                .thenThrow(new XException(ResponseCodeEnum.AGENT_MCP_NOT_FOUND));

        assertThrows(XException.class, () -> service.setEnabled(7L, false));
        verifyNoInteractions(agents);
    }

    @Test
    void changingOwnedMcpEnablementEvictsCurrentUsersRuntime() {
        McpServerManagementService configuration = mock(McpServerManagementService.class);
        HarnessAgentService agents = mock(HarnessAgentService.class);
        McpServerServiceImpl service = new McpServerServiceImpl(configuration, agents,
                mock(McpConnectionService.class));
        UserThreadLocalUtil.set(UserInfo.builder().userId("42").build());

        service.setEnabled(7L, false);

        verify(configuration).setEnabled("42", 7L, false);
        verify(agents).evictUserAgents("42");
    }
}
