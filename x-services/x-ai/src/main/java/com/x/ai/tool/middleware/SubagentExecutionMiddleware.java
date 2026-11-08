package com.x.ai.tool.middleware;

import com.x.ai.properties.AgentProperties;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class SubagentExecutionMiddleware implements MiddlewareBase {

    private static final Set<String> SUBAGENT_EXECUTION_TOOLS = Set.of("agent_spawn", "agent_send");

    private static final String TIMEOUT_SECONDS = "timeout_seconds";

    private final AgentProperties agentProperties;

    /**
     * 强制子 Agent 工具同步等待，使并行任务在主 Agent 继续前完成汇合。
     */
    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext context, ActingInput input,
                                     Function<ActingInput, Flux<AgentEvent>> next) {
        if (!agentProperties.getSubagent().isForceSync()) {
            return next.apply(input);
        }

        List<ToolUseBlock> toolCalls = new ArrayList<>(input.toolCalls().size());
        for (ToolUseBlock toolCall : input.toolCalls()) {
            toolCalls.add(forceSync(toolCall));
        }
        return next.apply(new ActingInput(toolCalls));
    }

    private ToolUseBlock forceSync(ToolUseBlock toolCall) {
        if (!SUBAGENT_EXECUTION_TOOLS.contains(toolCall.getName())) {
            return toolCall;
        }
        Map<String, Object> arguments = new LinkedHashMap<>(toolCall.getInput());
        arguments.put(TIMEOUT_SECONDS, agentProperties.getSubagent().getSyncTimeoutSeconds());
        return ToolUseBlock.builder()
                .id(toolCall.getId())
                .name(toolCall.getName())
                .input(arguments)
                .content(toolCall.getContent())
                .metadata(toolCall.getMetadata())
                .state(toolCall.getState())
                .build();
    }
}
