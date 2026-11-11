package com.x.ai.service.impl;

import com.x.ai.common.domain.McpConnectionResultDo;
import com.x.ai.common.domain.McpConnectionTestDo;
import com.x.ai.service.McpConnectionService;
import com.x.ai.service.McpServerManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class McpConnectionServiceImpl implements McpConnectionService {

    private final McpServerManagementService mcpServerManagementService;

    @Override
    public McpConnectionResultDo testConnection(String userId, Long id) {
        McpConnectionTestDo result = mcpServerManagementService.testConnection(userId, id);
        return new McpConnectionResultDo().setId(id)
                .setConnected(result.isConnected()).setToolNames(result.getToolNames());
    }
}
