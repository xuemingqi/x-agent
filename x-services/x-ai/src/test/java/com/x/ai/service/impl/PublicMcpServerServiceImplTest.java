package com.x.ai.service.impl;

import com.x.ai.mcp.api.PublicMcpRuntimeApi;
import com.x.ai.mcp.api.domain.McpServerDo;
import com.x.ai.mcp.api.dto.PublicMcpServerCreateDto;
import com.x.ai.mcp.api.dto.PublicMcpServerUpdateDto;
import com.x.ai.service.AdministratorAuthorizationService;
import com.x.ai.service.HarnessAgentService;
import com.x.ai.service.PublicResourceVersionService;
import com.x.ai.service.ResourceEnablementService;
import com.x.ai.common.model.ResourceEnablement;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublicMcpServerServiceImplTest {

    private final AdministratorAuthorizationService authorizationService = mock(AdministratorAuthorizationService.class);

    private final PublicMcpRuntimeApi runtimeApi = mock(PublicMcpRuntimeApi.class);

    private final HarnessAgentService harnessAgentService = mock(HarnessAgentService.class);

    private final PublicResourceVersionService versionService = mock(PublicResourceVersionService.class);

    private final ResourceEnablementService enablementService = mock(ResourceEnablementService.class);

    private final PublicMcpServerServiceImpl service = new PublicMcpServerServiceImpl(authorizationService,
            runtimeApi, harnessAgentService, versionService, enablementService);

    @Test
    void rejectsEveryPublicWriteBeforeCallingRuntimeForNonAdministrator() {
        doThrow(new XException(ResponseCodeEnum.PERMISSION_ERROR))
                .when(authorizationService).requireAdministrator("42");

        assertThrows(XException.class, () -> service.create("42", new PublicMcpServerCreateDto()));
        assertThrows(XException.class, () -> service.update("42", 1L, new PublicMcpServerUpdateDto()));
        assertThrows(XException.class, () -> service.delete("42", 1L));

        verifyNoInteractions(runtimeApi, harnessAgentService, versionService);
    }

    @Test
    void publicConfigurationChangeInvalidatesLocalAndClusterAgents() {
        PublicMcpServerUpdateDto request = new PublicMcpServerUpdateDto();
        McpServerDo updated = new McpServerDo().setId(1L);
        when(runtimeApi.update(1L, request)).thenReturn(updated);
        when(enablementService.get("42")).thenReturn(new ResourceEnablement(Set.of()));

        assertSame(updated, service.update("42", 1L, request));

        verify(authorizationService).requireAdministrator("42");
        verify(versionService).advanceVersion();
        verify(harnessAgentService).evictAllAgents();
    }
}
