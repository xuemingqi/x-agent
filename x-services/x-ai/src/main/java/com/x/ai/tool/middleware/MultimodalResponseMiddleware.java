package com.x.ai.tool.middleware;

import com.x.ai.common.model.ModelImageResponse;
import com.x.ai.decorator.MultimodalChatModelDecorator;
import com.x.framework.common.utils.StringUtil;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.TextBlockEndEvent;
import io.agentscope.core.event.TextBlockStartEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.state.AgentState;
import io.agentscope.harness.agent.middleware.HarnessRuntimeMiddleware;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 通过 2.x Middleware 补回 ReActAgent 聚合时丢失的图片，并同步回复和持久化上下文。
 */
@Component
public class MultimodalResponseMiddleware implements HarnessRuntimeMiddleware {

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext context, AgentInput input,
                                    Function<AgentInput, Flux<AgentEvent>> next) {
        return Flux.defer(() -> {
            ModelImageResponse response = new ModelImageResponse();
            return next.apply(input)
                    .filter(event -> !response.isTextPlaceholder() || !(event instanceof TextBlockStartEvent
                            || event instanceof TextBlockDeltaEvent || event instanceof TextBlockEndEvent))
                    .concatMap(event -> restoreResult(agent, event, response))
                    .contextWrite(reactorContext -> reactorContext.put(ModelImageResponse.class, response));
        });
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext context, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        return Flux.deferContextual(reactorContext -> {
            ModelImageResponse response = reactorContext.get(ModelImageResponse.class);
            response.setAgentState(RuntimeContext.resolveAgentState(context, agent));
            // 仅包装实际推理调用，压缩上下文等旁路调用不共享图片缓冲。
            return next.apply(new ModelCallInput(input.messages(), input.tools(), input.options(),
                    new MultimodalChatModelDecorator(input.model(), response)));
        });
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext context, ActingInput input,
                                     Function<ActingInput, Flux<AgentEvent>> next) {
        return Flux.deferContextual(reactorContext -> {
            // 此时 SDK 已将带工具调用的助手消息写入状态，下一轮推理可继续使用其中的图片。
            restoreState(reactorContext.get(ModelImageResponse.class));
            return next.apply(input);
        });
    }

    /**
     * SDK 在发出最终结果前已保存状态；补回图片后再次保存，再把完整结果交给调用方。
     */
    private Mono<AgentEvent> restoreResult(Agent agent, AgentEvent event, ModelImageResponse response) {
        if (!(event instanceof AgentResultEvent result) || !StringUtil.isBlank(event.getSource())
                || response.getImages().isEmpty()) {
            return Mono.just(event);
        }
        return Mono.fromCallable(() -> {
            Msg restored = restoreMessage(result.getResult(), response);
            if (restoreState(response)) {
                AgentState state = response.getAgentState();
                ((ReActAgent) agent).saveAgentState(state.getUserId(), state.getSessionId());
            }
            return new AgentResultEvent(event.getId(), event.getSource(), restored)
                    .withMetadata(event.getMetadata());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 只修改本次模型调用对应的助手消息，保留工具结果和其他轮次的上下文。
     */
    private boolean restoreState(ModelImageResponse response) {
        if (response.getImages().isEmpty()) {
            return false;
        }
        List<Msg> messages = response.getAgentState().contextMutable();
        for (int index = messages.size() - 1; index >= 0; index--) {
            Msg message = messages.get(index);
            if (!isResponseMessage(message, response)) {
                continue;
            }
            Msg restored = restoreMessage(message, response);
            if (restored != message) {
                messages.set(index, restored);
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * 按模型消息标识补回图片，并移除纯图片响应的内部占位文字。
     */
    private Msg restoreMessage(Msg message, ModelImageResponse response) {
        if (!isResponseMessage(message, response)) {
            return message;
        }
        List<ContentBlock> content = new ArrayList<>(message.getContent());
        if (response.isTextPlaceholder()) {
            content.removeIf(block -> block instanceof TextBlock);
        }
        response.getImages().stream().filter(block -> !content.contains(block)).forEach(content::add);
        return content.equals(message.getContent()) ? message : message.withContent(content);
    }

    /**
     * 使用模型响应标识定位当前助手消息，不把图片附加到中断提示等其他结果上。
     */
    private boolean isResponseMessage(Msg message, ModelImageResponse response) {
        return message != null && message.getRole() == MsgRole.ASSISTANT
                && Objects.equals(message.getId(), response.getLastResponse().getId());
    }
}
