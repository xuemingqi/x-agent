package com.x.ai.tool.middleware;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.transport.HttpRequest;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.remote.store.InMemoryStore;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterruptResponseMiddlewareTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void silentlyInterruptsAndResumesWithPreservedContext(boolean duringTool, @TempDir Path directory) throws Exception {
        CountDownLatch waiting = new CountDownLatch(1);
        Sinks.One<String> modelCompletion = Sinks.one();
        Sinks.One<ToolResultBlock> toolCompletion = Sinks.one();
        HttpTransport transport = mock(HttpTransport.class);
        Flux<String> firstResponse = duringTool ? Flux.just(chunk("""
                {"tool_calls":[{"index":0,"id":"tool-1","type":"function",
                "function":{"name":"inspect","arguments":"{}"}}]}
                """, "tool_calls"), "[DONE]") : Flux.just(chunk("{\"content\":\"已完成页面布局\"}", null))
                .concatWith(Flux.defer(() -> {
                    waiting.countDown();
                    return modelCompletion.asMono();
                })).concatWithValues("[DONE]");
        when(transport.stream(any())).thenReturn(firstResponse,
                Flux.just(chunk("{\"content\":\"已按新方向继续\"}", "stop"), "[DONE]"));
        AgentTool tool = mock(AgentTool.class);
        when(tool.getName()).thenReturn("inspect");
        when(tool.getDescription()).thenReturn("检查页面");
        when(tool.getParameters()).thenReturn(Map.of("type", "object", "properties", Map.of()));
        when(tool.callAsync(any())).thenReturn(toolCompletion.asMono().doOnSubscribe(ignored -> waiting.countDown()));
        Toolkit toolkit = new Toolkit();
        toolkit.registerAgentTool(tool);
        JsonFileAgentStateStore stateStore = new JsonFileAgentStateStore(directory.resolve("state"));
        HarnessAgent agent = HarnessAgent.builder().name("interrupt-test")
                .model(OpenAIChatModel.builder().modelName("test-model").apiKey("test-key").httpTransport(transport).build())
                .workspace(directory.resolve("workspace"))
                .distributedStore(DistributedStore.builder().agentStateStore(stateStore).baseStore(new InMemoryStore()).build())
                .toolkit(toolkit).permissionContext(PermissionContextState.builder().mode(PermissionMode.BYPASS).build())
                .enablePendingToolRecovery(true)
                .disableFilesystemTools().disableShellTool().disableSubagents().disableDynamicSkills()
                .disableDefaultWorkspaceSkills().disableWorkspaceContext().disableAtPathExpansion()
                .disableMemoryHooks().disableMemoryTools().disableCompaction().disableToolResultEviction()
                .disableToolsConfig().middleware(new MultimodalResponseMiddleware())
                .middleware(new InterruptResponseMiddleware()).build();
        RuntimeContext context = RuntimeContext.builder().userId("user-1").sessionId("session-1").build();
        try {
            var running = agent.streamEvents(new UserMessage("生成五子棋页面"), context).collectList().toFuture();
            assertThat(waiting.await(5, TimeUnit.SECONDS)).isTrue();
            agent.getDelegate().interrupt("user-1", "session-1");
            // SDK 在推理或工具边界处理中断；不能通过断开订阅冒充执行结束。
            modelCompletion.tryEmitValue(chunk("{}", "stop"));
            toolCompletion.tryEmitValue(ToolResultBlock.text("页面检查完成"));
            List<AgentEvent> events = running.get(5, TimeUnit.SECONDS);
            Msg result = events.stream().filter(AgentResultEvent.class::isInstance).map(AgentResultEvent.class::cast)
                    .map(AgentResultEvent::getResult).filter(message -> message.getGenerateReason() == GenerateReason.INTERRUPTED)
                    .findFirst().orElseThrow();
            assertThat(result.getTextContent()).isEmpty();
            assertThat(events.getLast()).isInstanceOf(AgentEndEvent.class);
            assertThat(events.stream().filter(TextBlockDeltaEvent.class::isInstance).map(TextBlockDeltaEvent.class::cast)
                    .map(TextBlockDeltaEvent::getDelta)).noneMatch(text -> text.contains("interrupted me"));
            if (!duringTool) {
                assertThat(events.stream().filter(TextBlockDeltaEvent.class::isInstance).map(TextBlockDeltaEvent.class::cast)
                        .map(TextBlockDeltaEvent::getDelta)).contains("已完成页面布局");
            }
            AgentState saved = stateStore.get("user-1", "session-1", "agent_state", AgentState.class).orElseThrow();
            assertThat(saved.getContext()).noneMatch(message -> message.getGenerateReason() == GenerateReason.INTERRUPTED);

            // 重载持久化状态后继续，确保移除的问候不会在下一轮模型输入中重新出现。
            agent.clearStateCache();
            Msg next = agent.call(new UserMessage("改成深色主题"), context).block(Duration.ofSeconds(5));
            assertThat(next.getTextContent()).isEqualTo("已按新方向继续");
            ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
            verify(transport, times(2)).stream(requests.capture());
            assertThat(requests.getAllValues().getLast().getBody()).contains("生成五子棋页面", "改成深色主题")
                    .doesNotContain("interrupted me", "What can I do for you?");
            if (duringTool) {
                assertThat(requests.getAllValues().getLast().getBody()).contains("tool-1", "inspect", "页面检查完成");
            } else {
                assertThat(requests.getAllValues().getLast().getBody()).contains("已完成页面布局");
            }
        } finally {
            agent.close();
        }
    }

    private String chunk(String delta, String finishReason) {
        return "{\"id\":\"reply\",\"object\":\"chat.completion.chunk\",\"model\":\"test-model\",\"choices\":[{\"index\":0,"
                + "\"delta\":" + delta + ",\"finish_reason\":" + (finishReason == null ? "null" : "\"" + finishReason + "\"") + "}]}";
    }
}
