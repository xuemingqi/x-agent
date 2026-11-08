package com.x.ai.mcp.db.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.x.ai.mcp.db.entity.AgentMcpServer;
import com.x.ai.mcp.api.enums.McpScope;

public interface AgentMcpServerIService extends IService<AgentMcpServer> {

    /**
     * 构建公共配置和指定用户私有配置的可见范围查询。
     *
     * @param userId 用户 ID
     * @return MCP 可见范围查询
     */
    default LambdaQueryChainWrapper<AgentMcpServer> visibleQuery(String userId) {
        return lambdaQuery().and(wrapper -> wrapper
                .in(AgentMcpServer::getScope, McpScope.PUBLIC, McpScope.INTERNAL_PUBLIC)
                .or(nested -> nested
                        .eq(AgentMcpServer::getScope, McpScope.USER)
                        .eq(AgentMcpServer::getOwnerUserId, userId)));
    }
}
