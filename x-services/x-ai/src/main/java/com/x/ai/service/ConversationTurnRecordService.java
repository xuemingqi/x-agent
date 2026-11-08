package com.x.ai.service;

import io.agentscope.core.event.AgentEvent;
import reactor.core.publisher.Flux;

public interface ConversationTurnRecordService {

    /**
     * 记录本轮 Agent 事件，并在事件流正常结束后保存用户可见消息。
     *
     * @param userId          用户 ID
     * @param sessionId       会话 ID
     * @param userMessageId   本轮用户消息 ID
     * @param events          Agent 事件流
     * @return 保持原始顺序的 Agent 事件流
     */
    Flux<AgentEvent> record(String userId, String sessionId, Long userMessageId, Flux<AgentEvent> events);
}
