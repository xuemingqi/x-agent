package com.x.ai.db.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.x.ai.db.entity.AgentMcpServer;

public interface AgentMcpServerIService extends IService<AgentMcpServer> {

    /**
     * 所有读取入口先限定所有者，避免列表、检测和运行时绕过用户隔离。
     */
    default LambdaQueryChainWrapper<AgentMcpServer> ownedQuery(String userId) {
        return lambdaQuery().eq(AgentMcpServer::getOwnerUserId, userId);
    }
}
