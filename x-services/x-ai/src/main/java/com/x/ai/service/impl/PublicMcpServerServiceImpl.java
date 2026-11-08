package com.x.ai.service.impl;

import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.mcp.api.PublicMcpRuntimeApi;
import com.x.ai.mcp.api.domain.McpServerDo;
import com.x.ai.mcp.api.dto.PublicMcpServerCreateDto;
import com.x.ai.mcp.api.dto.PublicMcpServerUpdateDto;
import com.x.ai.service.AdministratorAuthorizationService;
import com.x.ai.service.HarnessAgentService;
import com.x.ai.service.PublicMcpServerService;
import com.x.ai.service.PublicResourceVersionService;
import com.x.ai.service.ResourceEnablementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicMcpServerServiceImpl implements PublicMcpServerService {

    private final AdministratorAuthorizationService authorizationService;

    private final PublicMcpRuntimeApi publicMcpRuntimeApi;

    private final HarnessAgentService harnessAgentService;

    private final PublicResourceVersionService publicResourceVersionService;

    private final ResourceEnablementService resourceEnablementService;


    @Override
    public List<McpServerDo> list(String actorUserId) {
        authorizationService.requireAdministrator(actorUserId);
        List<McpServerDo> servers = publicMcpRuntimeApi.list();
        ResourceEnablement enablement = resourceEnablementService.get(actorUserId);
        servers.forEach(server -> server.setUserEnabled(enablement.isMcpEnabled(server.getId())));
        return servers;
    }

    @Override
    public McpServerDo get(String actorUserId, Long id) {
        authorizationService.requireAdministrator(actorUserId);
        return publicMcpRuntimeApi.get(id)
                .setUserEnabled(resourceEnablementService.get(actorUserId).isMcpEnabled(id));
    }

    @Override
    public McpServerDo create(String actorUserId, PublicMcpServerCreateDto request) {
        authorizationService.requireAdministrator(actorUserId);
        McpServerDo server = publicMcpRuntimeApi.create(request);
        invalidateAgentRuntimes();
        return server;
    }

    @Override
    public McpServerDo update(String actorUserId, Long id, PublicMcpServerUpdateDto request) {
        authorizationService.requireAdministrator(actorUserId);
        McpServerDo server = publicMcpRuntimeApi.update(id, request)
                .setUserEnabled(resourceEnablementService.get(actorUserId).isMcpEnabled(id));
        invalidateAgentRuntimes();
        return server;
    }

    @Override
    public boolean delete(String actorUserId, Long id) {
        authorizationService.requireAdministrator(actorUserId);
        boolean deleted = publicMcpRuntimeApi.delete(id);
        if (deleted) {
            invalidateAgentRuntimes();
        }
        return deleted;
    }

    /**
     * 推进集群版本，并立即淘汰本节点的 Agent。
     */
    private void invalidateAgentRuntimes() {
        publicResourceVersionService.advanceVersion();
        harnessAgentService.evictAllAgents();
    }

}
