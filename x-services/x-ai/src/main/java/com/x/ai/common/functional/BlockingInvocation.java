package com.x.ai.common.functional;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface BlockingInvocation {

    /**
     * 执行一轮阻塞式智能体调用。
     *
     * @param message 本轮输入消息
     * @param context 运行时上下文
     * @return 本轮生成结果
     */
    Mono<Msg> call(Msg message, RuntimeContext context);
}
