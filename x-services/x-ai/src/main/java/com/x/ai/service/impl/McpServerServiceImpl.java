package com.x.ai.service.impl;

import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.mcp.api.McpRuntimeApi;
import com.x.ai.mcp.api.domain.McpConnectionTestDo;
import com.x.ai.mcp.api.domain.McpServerDo;
import com.x.ai.mcp.api.dto.McpServerCreateDto;
import com.x.ai.mcp.api.dto.McpServerUpdateDto;
import com.x.ai.mcp.api.dto.McpUserCredentialUpdateDto;
import com.x.ai.mcp.api.enums.McpScope;
import com.x.ai.service.HarnessAgentService;
import com.x.ai.service.McpConnectionService;
import com.x.ai.service.McpServerService;
import com.x.ai.service.ResourceEnablementService;
import com.x.framework.common.utils.UserThreadLocalUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class McpServerServiceImpl implements McpServerService {

    private final McpRuntimeApi mcpRuntimeApi;

    private final HarnessAgentService harnessAgentService;

    private final McpConnectionService mcpConnectionService;

    private final ResourceEnablementService resourceEnablementService;

    @Override
    public List<McpServerDo> list(McpScope scope) {
        String userId = currentUserId();
        ResourceEnablement enablement = resourceEnablementService.get(userId);
        List<McpServerDo> servers = mcpRuntimeApi.list(userId, scope == null ? null : scope.getCode());
        servers.forEach(server -> server.setUserEnabled(enablement.isMcpEnabled(server.getId())));
        return servers;
    }

    @Override
    public boolean setEnabled(McpScope scope, Long id, boolean enabled) {
        String userId = currentUserId();
        mcpRuntimeApi.get(userId, scope.getCode(), id);
        resourceEnablementService.setEnabled(userId, ResourceEnablement.mcpKey(id), enabled);
        return enabled;
    }

    @Override
    public McpServerDo get(McpScope scope, Long id) {
        String userId = currentUserId();
        return mcpRuntimeApi.get(userId, scope.getCode(), id)
                .setUserEnabled(resourceEnablementService.get(userId).isMcpEnabled(id));
    }

    @Override
    public McpServerDo create(McpServerCreateDto request) {
        String userId = currentUserId();
        McpServerDo server = mcpRuntimeApi.create(userId, request);
        harnessAgentService.evictUserAgents(userId);
        return server;
    }

    @Override
    public McpServerDo update(Long id, McpServerUpdateDto request) {
        String userId = currentUserId();
        McpServerDo server = mcpRuntimeApi.update(userId, id, request)
                .setUserEnabled(resourceEnablementService.get(userId).isMcpEnabled(id));
        harnessAgentService.evictUserAgents(userId);
        return server;
    }

    @Override
    public McpServerDo updateUserCredential(Long id, McpUserCredentialUpdateDto request) {
        String userId = currentUserId();
        McpServerDo server = mcpRuntimeApi.updateUserCredential(userId, id, request)
                .setUserEnabled(resourceEnablementService.get(userId).isMcpEnabled(id));
        harnessAgentService.evictUserAgents(userId);
        return server;
    }

    @Override
    public boolean delete(Long id) {
        String userId = currentUserId();
        boolean deleted = mcpRuntimeApi.delete(userId, id);
        if (deleted) {
            harnessAgentService.evictUserAgents(userId);
        }
        return deleted;
    }

    @Override
    public McpConnectionTestDo testConnection(McpScope scope, Long id) {
        var result = mcpConnectionService.testConnection(currentUserId(), scope, id);
        return new McpConnectionTestDo().setConnected(result.isConnected()).setToolNames(result.getToolNames());
    }

    /**
     * 使用现有登录鉴权写入的线程上下文获取当前用户。
     */
    private String currentUserId() {
        return UserThreadLocalUtil.getUserId();
    }
}
