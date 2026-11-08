package com.x.ai.service.impl;

import com.x.ai.common.domain.McpConnectionResultDo;
import com.x.ai.mcp.api.McpRuntimeApi;
import com.x.ai.mcp.api.domain.McpConnectionTestDo;
import com.x.ai.mcp.api.domain.McpServerDo;
import com.x.ai.mcp.api.enums.McpScope;
import com.x.ai.service.McpConnectionService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class McpConnectionServiceImpl implements McpConnectionService {

    private final McpRuntimeApi mcpRuntimeApi;

    @Override
    public McpConnectionResultDo testConnection(String userId, McpScope scope, Long id) {
        if (scope == null) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        try {
            McpServerDo server = mcpRuntimeApi.get(userId, scope.getCode(), id);
            McpConnectionTestDo result = mcpRuntimeApi.testConnection(userId, server.getScope().getCode(), id);
            return new McpConnectionResultDo().setId(server.getId()).setScope(server.getScope())
                    .setConnected(result.isConnected())
                    .setToolNames(result.getToolNames() == null ? List.of() : result.getToolNames());
        } catch (XException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // 远程错误可能携带响应内容和凭证，只向调用方返回稳定业务错误。
            throw new XException(ResponseCodeEnum.AGENT_MCP_CONNECTION_ERROR);
        }
    }
}
