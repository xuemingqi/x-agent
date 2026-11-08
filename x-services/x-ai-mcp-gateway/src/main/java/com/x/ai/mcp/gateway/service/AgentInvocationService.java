package com.x.ai.mcp.gateway.service;

import com.x.api.ai.domain.ExternalAgentResponseDo;

/**
 * X Agent 外部调用服务。
 *
 * @author xuemingqi
 */
public interface AgentInvocationService {

    /**
     * 使用 API Key 阻塞调用 Agent。
     *
     * @param question 问题内容
     * @param sessionId 会话 ID，为空时由 Agent 服务生成
     * @return Agent 调用结果
     */
    ExternalAgentResponseDo run(String question, String sessionId);
}
