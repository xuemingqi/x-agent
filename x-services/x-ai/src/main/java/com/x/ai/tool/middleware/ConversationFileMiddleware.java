package com.x.ai.tool.middleware;

import com.x.ai.service.ConversationFileService;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.harness.agent.middleware.HarnessRuntimeMiddleware;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class ConversationFileMiddleware implements HarnessRuntimeMiddleware {

    private final ConversationFileService conversationFileService;

    /**
     * 在 AgentScope 沙箱生命周期完成 acquire/start 后恢复本次会话的附件与产物。
     */
    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext context, AgentInput input, Function<AgentInput, Flux<AgentEvent>> next) {
        return Flux.defer(() -> {
            conversationFileService.materializeFiles(context);
            return next.apply(input);
        });
    }
}
