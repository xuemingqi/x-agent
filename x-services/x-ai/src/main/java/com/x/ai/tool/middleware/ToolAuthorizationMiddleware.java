package com.x.ai.tool.middleware;

import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.ai.common.functional.ToolAuthorizationService;
import com.x.ai.common.model.ToolContext;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class ToolAuthorizationMiddleware implements MiddlewareBase {

    private final ToolAuthorizationService authorizationService;


    /**
     * 校验运行时中的身份快照与用户标识一致，并将业务授权通过的工具调用标记为已批准。
     */
    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext context, ActingInput input, Function<ActingInput, Flux<AgentEvent>> next) {
        ToolContext toolContext = context == null ? null : context.get(ToolContext.class);
        if (toolContext == null || !Objects.equals(context.getUserId(), toolContext.getUserId())) {
            return Flux.error(new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID));
        }
        List<ToolUseBlock> authorizedToolCalls = new ArrayList<>(input.toolCalls().size());
        for (ToolUseBlock toolCall : input.toolCalls()) {
            authorizationService.authorize(toolContext, toolCall);
            authorizedToolCalls.add(toolCall.withState(ToolCallState.ALLOWED));
        }
        return next.apply(new ActingInput(authorizedToolCalls));
    }
}
