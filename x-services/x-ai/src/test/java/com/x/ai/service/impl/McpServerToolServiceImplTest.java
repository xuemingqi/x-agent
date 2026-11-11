package com.x.ai.service.impl;

import com.x.ai.common.domain.McpConnectionResultDo;
import com.x.ai.common.domain.McpServerDo;
import com.x.ai.common.dto.McpServerToolCreateDto;
import com.x.ai.common.dto.McpServerToolDto;
import com.x.ai.common.dto.McpServerToolUpdateDto;
import com.x.ai.common.model.ToolContext;
import com.x.ai.service.HarnessAgentService;
import com.x.ai.service.McpConnectionService;
import com.x.ai.service.McpServerManagementService;
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

    private final McpServerManagementService runtimeApi = mock(McpServerManagementService.class);

    private final McpConnectionService connectionService = mock(McpConnectionService.class);

    private final McpServerToolServiceImpl service = new McpServerToolServiceImpl(runtimeApi,
            mock(HarnessAgentService.class), connectionService);

    @Test
    void preservesLargeIdsAndRemovesCredentialValuesFromToolOutput() {
        long id = 9007199254740993L;
        when(runtimeApi.list("42")).thenReturn(List.of(new McpServerDo().setId(id)
                .setHeaders(Map.of("Authorization", "private-token"))
                .setQueryParameters(Map.of("key", "query-secret"))));

        McpServerToolDto result = service.list(new ToolContext().setUserId("42")).getFirst();

        assertEquals("9007199254740993", result.getId());
        assertEquals(Map.of("Authorization", ""), result.getHeaders());
        assertEquals(Map.of("key", ""), result.getQueryParameters());
    }

    @Test
    void connectionTestUsesTrustedToolIdentity() {
        when(connectionService.testConnection("42", 99L))
                .thenReturn(new McpConnectionResultDo().setId(99L)
                        .setConnected(true).setToolNames(List.of("search")));

        var result = service.testConnection(new ToolContext().setUserId("42"), 99L);

        assertEquals("99", result.getId());
        assertEquals(List.of("search"), result.getToolNames());
        verify(connectionService).testConnection("42", 99L);
    }

    @Test
    void rejectsMissingIdentityBeforeConfigurationAccess() {
        assertThrows(XException.class, () -> service.list(null));
        assertThrows(XException.class, () -> service.testConnection(new ToolContext(), 1L));
        verifyNoInteractions(runtimeApi, connectionService);
    }

    @Test
    void rejectsRemovedTransportForBothCreateAndUpdate() {
        ToolContext context = new ToolContext().setUserId("42");

        assertThrows(XException.class, () -> service.create(context,
                new McpServerToolCreateDto().setTransportType(2)));
        assertThrows(XException.class, () -> service.update(context, 7L,
                new McpServerToolUpdateDto().setTransportType(2)));

        verifyNoInteractions(runtimeApi, connectionService);
    }
}
