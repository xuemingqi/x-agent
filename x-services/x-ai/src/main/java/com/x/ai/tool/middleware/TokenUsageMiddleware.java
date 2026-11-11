package com.x.ai.tool.middleware;

import com.x.ai.common.model.TokenUsageAccumulator;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.function.Function;

/**
 * 在模型调用边界统计用量，同步和流式入口共享同一统计口径。
 */
@Component
public class TokenUsageMiddleware implements MiddlewareBase {

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext context, ModelCallInput input,
                                       Function<ModelCallInput, Flux<AgentEvent>> next) {
        TokenUsageAccumulator usage = context.get(TokenUsageAccumulator.class);
        return next.apply(input).doOnNext(event -> {
            if (usage != null && event instanceof ModelCallEndEvent end) {
                usage.record(end);
            }
        });
    }
}
