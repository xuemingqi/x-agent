package com.x.ai.common.functional;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import reactor.core.publisher.Flux;

@FunctionalInterface
public interface StreamingInvocation {

    /**
     * 执行一轮流式智能体调用。
     *
     * @param message 本轮输入消息
     * @param context 运行时上下文
     * @return 本轮智能体事件流
     */
    Flux<AgentEvent> stream(Msg message, RuntimeContext context);
}
