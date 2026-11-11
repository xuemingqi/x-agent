package com.x.ai.service.impl;

import com.x.ai.common.domain.McpConnectionTestDo;
import com.x.ai.common.domain.McpServerDo;
import com.x.ai.common.dto.McpServerCreateDto;
import com.x.ai.common.dto.McpServerUpdateDto;
import com.x.ai.service.HarnessAgentService;
import com.x.ai.service.McpConnectionService;
import com.x.ai.service.McpServerManagementService;
import com.x.ai.service.McpServerService;
import com.x.framework.common.utils.UserThreadLocalUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class McpServerServiceImpl implements McpServerService {

    private final McpServerManagementService mcpServerManagementService;

    private final HarnessAgentService harnessAgentService;

    private final McpConnectionService mcpConnectionService;

    @Override
    public List<McpServerDo> list() {
        return mcpServerManagementService.list(currentUserId());
    }

    @Override
    public boolean setEnabled(Long id, boolean enabled) {
        String userId = currentUserId();
        mcpServerManagementService.setEnabled(userId, id, enabled);
        harnessAgentService.evictUserAgents(userId);
        return enabled;
    }

    @Override
    public McpServerDo get(Long id) {
        return mcpServerManagementService.get(currentUserId(), id);
    }

    @Override
    public McpServerDo create(McpServerCreateDto request) {
        String userId = currentUserId();
        McpServerDo server = mcpServerManagementService.create(userId, request);
        harnessAgentService.evictUserAgents(userId);
        return server;
    }

    @Override
    public McpServerDo update(Long id, McpServerUpdateDto request) {
        String userId = currentUserId();
        McpServerDo server = mcpServerManagementService.update(userId, id, request);
        harnessAgentService.evictUserAgents(userId);
        return server;
    }

    @Override
    public boolean delete(Long id) {
        String userId = currentUserId();
        boolean deleted = mcpServerManagementService.delete(userId, id);
        if (deleted) {
            harnessAgentService.evictUserAgents(userId);
        }
        return deleted;
    }

    @Override
    public McpConnectionTestDo testConnection(Long id) {
        var result = mcpConnectionService.testConnection(currentUserId(), id);
        return new McpConnectionTestDo().setConnected(result.isConnected()).setToolNames(result.getToolNames());
    }

    /**
     * 使用现有登录鉴权写入的线程上下文获取当前用户。
     */
    private String currentUserId() {
        return UserThreadLocalUtil.getUserId();
    }
}
