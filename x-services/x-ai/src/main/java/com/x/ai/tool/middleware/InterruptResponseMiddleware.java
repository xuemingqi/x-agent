package com.x.ai.tool.middleware;

import com.x.framework.common.utils.StringUtil;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.state.AgentState;
import io.agentscope.harness.agent.middleware.HarnessRuntimeMiddleware;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 将 SDK 的用户中断结果转换为控制信号，避免暂停问候进入回复和下一轮模型上下文。
 */
@Component
public class InterruptResponseMiddleware implements HarnessRuntimeMiddleware {

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext context, AgentInput input,
                                    Function<AgentInput, Flux<AgentEvent>> next) {
        return next.apply(input).concatMap(event -> normalizeResult(agent, context, event));
    }

    private Mono<AgentEvent> normalizeResult(Agent agent, RuntimeContext context, AgentEvent event) {
        if (!(event instanceof AgentResultEvent result) || !StringUtil.isBlank(event.getSource())
                || result.getResult() == null || result.getResult().getGenerateReason() != GenerateReason.INTERRUPTED) {
            return Mono.just(event);
        }
        return Mono.fromCallable(() -> {
            Msg interrupted = result.getResult();
            AgentState state = RuntimeContext.resolveAgentState(context, agent);
            if (state.contextMutable().removeIf(message -> Objects.equals(message.getId(), interrupted.getId()))) {
                ((ReActAgent) agent).saveAgentState(state.getUserId(), state.getSessionId());
            }
            // 保留中断原因供调用方结束任务，已输出的文本和工具结果由原有事件流保留。
            return new AgentResultEvent(event.getId(), event.getSource(), interrupted.withContent(List.of()))
                    .withMetadata(event.getMetadata());
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
