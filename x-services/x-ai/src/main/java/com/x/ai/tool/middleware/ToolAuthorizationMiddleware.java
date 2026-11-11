package com.x.ai.tool.middleware;

import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.ai.common.model.ToolContext;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Component
public class ToolAuthorizationMiddleware implements MiddlewareBase {

    /**
     * 校验运行时中的身份快照与用户标识一致，并将格式有效的工具调用标记为已批准。
     */
    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext context, ActingInput input, Function<ActingInput, Flux<AgentEvent>> next) {
        ToolContext toolContext = context == null ? null : context.get(ToolContext.class);
        if (toolContext == null || toolContext.getUserId() == null || toolContext.getUserId().isBlank()
                || !Objects.equals(context.getUserId(), toolContext.getUserId())) {
            return Flux.error(new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID));
        }
        List<ToolUseBlock> authorizedToolCalls = new ArrayList<>(input.toolCalls().size());
        for (ToolUseBlock toolCall : input.toolCalls()) {
            if (toolCall == null || toolCall.getName() == null || toolCall.getName().isBlank()) {
                return Flux.error(new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID));
            }
            authorizedToolCalls.add(toolCall.withState(ToolCallState.ALLOWED));
        }
        return next.apply(new ActingInput(authorizedToolCalls));
    }
}
