package com.x.ai.service.impl;

import com.x.ai.common.domain.McpConnectionResultDo;
import com.x.ai.common.dto.McpServerToolDto;
import com.x.ai.common.model.ToolContext;
import com.x.ai.mcp.api.McpRuntimeApi;
import com.x.ai.mcp.api.domain.McpServerDo;
import com.x.ai.mcp.api.enums.McpScope;
import com.x.ai.service.HarnessAgentService;
import com.x.ai.service.McpConnectionService;
import com.x.framework.common.exception.XException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McpServerToolServiceImplTest {

    private final McpRuntimeApi runtimeApi = mock(McpRuntimeApi.class);

    private final McpConnectionService connectionService = mock(McpConnectionService.class);

    private final McpServerToolServiceImpl service = new McpServerToolServiceImpl(runtimeApi,
            mock(HarnessAgentService.class), connectionService);

    @Test
    void preservesLargeIdsAndRemovesCredentialValuesFromToolOutput() {
        long id = 9007199254740993L;
        when(runtimeApi.list("42", null)).thenReturn(List.of(new McpServerDo().setId(id).setScope(McpScope.USER)
                .setHeaders(Map.of("Authorization", "private-token"))
                .setQueryParameters(Map.of("key", "query-secret"))
                .setEnvironment(Map.of("SECRET", "environment-secret"))));

        McpServerToolDto result = service.list(new ToolContext().setUserId("42"), null).getFirst();

        assertEquals("9007199254740993", result.getId());
        assertEquals(Map.of("Authorization", ""), result.getHeaders());
        assertEquals(Map.of("key", ""), result.getQueryParameters());
        assertEquals(Map.of("SECRET", ""), result.getEnvironment());
    }

    @Test
    void connectionTestUsesTrustedToolIdentity() {
        when(connectionService.testConnection("42", McpScope.USER, 99L))
                .thenReturn(new McpConnectionResultDo().setId(99L).setScope(McpScope.USER)
                        .setConnected(true).setToolNames(List.of("search")));

        var result = service.testConnection(new ToolContext().setUserId("42"), 2, 99L);

        assertEquals("99", result.getId());
        assertEquals(List.of("search"), result.getToolNames());
        verify(connectionService).testConnection("42", McpScope.USER, 99L);
    }

    @Test
    void rejectsMissingIdentityAndUnknownScopeBeforeRemoteAccess() {
        assertThrows(XException.class, () -> service.list(null, null));
        assertThrows(XException.class, () -> service.testConnection(new ToolContext().setUserId("42"), 99, 1L));
        verifyNoInteractions(runtimeApi, connectionService);
    }
}
