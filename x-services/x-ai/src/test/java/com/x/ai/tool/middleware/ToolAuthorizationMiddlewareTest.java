package com.x.ai.tool.middleware;

import com.x.ai.common.model.ToolContext;
import com.x.framework.common.exception.XException;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolAuthorizationMiddlewareTest {

    private final ToolAuthorizationMiddleware middleware = new ToolAuthorizationMiddleware();

    @Test
    void ordinaryUserExecutesValidCallsWithTheTrustedIdentity() {
        RuntimeContext context = RuntimeContext.builder().userId("42")
                .put(ToolContext.class, new ToolContext().setUserId("42")).build();
        ToolUseBlock call = new ToolUseBlock("call-1", "knowledge_link_list", Map.of());
        AtomicBoolean executed = new AtomicBoolean();

        middleware.onActing(null, context, new ActingInput(List.of(call)), input -> {
            assertThat(input.toolCalls()).hasSize(1);
            assertThat(input.toolCalls().getFirst().getState()).isEqualTo(ToolCallState.ALLOWED);
            assertThat(input.toolCalls().getFirst().getName()).isEqualTo("knowledge_link_list");
            executed.set(true);
            return Flux.<AgentEvent>empty();
        }).blockLast();

        assertThat(executed).isTrue();
        assertThat(call.getState()).isEqualTo(ToolCallState.PENDING);
    }

    @Test
    void mismatchedOrMissingToolIdentityNeverReachesExecution() {
        List<RuntimeContext> contexts = List.of(RuntimeContext.empty(),
                RuntimeContext.builder().put(ToolContext.class, new ToolContext()).build(),
                RuntimeContext.builder().userId("42")
                        .put(ToolContext.class, new ToolContext().setUserId("other-user")).build());
        AtomicBoolean executed = new AtomicBoolean();
        for (RuntimeContext context : contexts) {
            assertThatThrownBy(() -> middleware.onActing(null, context, new ActingInput(List.of()), input -> {
                executed.set(true);
                return Flux.empty();
            }).blockLast()).isInstanceOf(XException.class);
        }
        assertThat(executed).isFalse();
    }

    @Test
    void invalidToolNameNeverReachesExecution() {
        RuntimeContext context = RuntimeContext.builder().userId("42")
                .put(ToolContext.class, new ToolContext().setUserId("42")).build();
        AtomicBoolean executed = new AtomicBoolean();

        assertThatThrownBy(() -> middleware.onActing(null, context,
                new ActingInput(List.of(new ToolUseBlock("call-1", " ", Map.of()))), input -> {
                    executed.set(true);
                    return Flux.empty();
                }).blockLast()).isInstanceOf(XException.class);

        assertThat(executed).isFalse();
    }
}
