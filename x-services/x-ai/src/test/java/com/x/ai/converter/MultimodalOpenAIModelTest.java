package com.x.ai.converter;

import com.x.ai.decorator.MultimodalOpenAITransportDecorator;
import com.x.ai.tool.middleware.MultimodalResponseMiddleware;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.transport.HttpRequest;
import io.agentscope.core.model.transport.HttpResponse;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultimodalOpenAIModelTest {

    private static final String IMAGE_URL = "data:image/png;base64,aW1hZ2U=";

    @Test
    void sendsNativeImageContentAndRequestedOutputModalities() {
        HttpTransport transport = mock(HttpTransport.class);
        when(transport.stream(any())).thenReturn(Flux.just(chunk("{\"content\":\"收到\"}", "stop"), "[DONE]"));
        OpenAIChatModel model = model(transport, true, true);
        UserMessage input = new UserMessage(TextBlock.builder().text("描述图片").build(), DataBlock.builder()
                .source(Base64Source.builder().mediaType("image/png").data("aW1hZ2U=").build()).build());

        model.stream(List.of(input), List.of(), GenerateOptions.builder()
                .additionalBodyParam("modalities", List.of("text", "image")).build()).collectList().block();

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(transport).stream(request.capture());
        assertThat(request.getValue().getBody()).contains(IMAGE_URL, "image_url", "modalities", "描述图片");
    }

    @ParameterizedTest
    @ValueSource(strings = {"画好了", ""})
    void preservesImagesAfterReloadWithoutLeakingIntoOtherRepliesOrUsers(String replyText, @TempDir Path directory) {
        HttpTransport transport = mock(HttpTransport.class);
        when(transport.stream(any())).thenReturn(Flux.just(
                chunk("{\"content\":\"" + replyText + "\",\"images\":[{\"type\":\"image_url\",\"image_url\":{\"url\":\""
                        + IMAGE_URL + "\"}}]}", null), chunk("{}", "stop"), "[DONE]"),
                Flux.just(chunk("{\"content\":\"这张图的说明\"}", "stop"), "[DONE]"),
                Flux.just(chunk("{\"content\":\"其他用户回复\"}", "stop"), "[DONE]"));
        MultimodalResponseMiddleware middleware = new MultimodalResponseMiddleware();
        JsonFileAgentStateStore stateStore = new JsonFileAgentStateStore(directory);
        RuntimeContext context = RuntimeContext.builder().userId("user-1").sessionId("session-1").build();
        try (ReActAgent agent = ReActAgent.builder().name("image-test")
                    .model(model(transport, true, true))
                    .stateStore(stateStore)
                    .middleware(middleware).build()) {

            List<AgentEvent> events = agent.streamEvents(new UserMessage("画一张图片"), context)
                    .collectList().block(Duration.ofSeconds(10));
            Msg result = events.stream().filter(AgentResultEvent.class::isInstance).map(AgentResultEvent.class::cast)
                    .map(AgentResultEvent::getResult).findFirst().orElseThrow();

            assertThat(result.getTextContent()).isEqualTo(replyText);
            assertThat(events.stream().filter(TextBlockDeltaEvent.class::isInstance).map(TextBlockDeltaEvent.class::cast)
                    .map(TextBlockDeltaEvent::getDelta).collect(java.util.stream.Collectors.joining())).isEqualTo(replyText);
            assertThat(result.getContentBlocks(DataBlock.class)).hasSize(1);
            assertThat(((URLSource) result.getContentBlocks(DataBlock.class).getFirst().getSource()).getUrl())
                    .isEqualTo(IMAGE_URL);

            AgentState saved = stateStore.get("user-1", "session-1", "agent_state", AgentState.class).orElseThrow();
            assertThat(saved.getContext().getLast().getContentBlocks(DataBlock.class)).hasSize(1);
            assertThat(saved.getContext().getLast().getTextContent()).isEqualTo(replyText);
            agent.clearStateCache();
            Msg next = agent.call("解释上一张图",
                    RuntimeContext.builder().userId("user-1").sessionId("session-1").build()).block(Duration.ofSeconds(10));
            assertThat(next.getTextContent()).isEqualTo("这张图的说明");
            assertThat(next.getContentBlocks(DataBlock.class)).isEmpty();
            Msg other = agent.call("另一位用户的提问",
                    RuntimeContext.builder().userId("user-2").sessionId("session-1").build()).block(Duration.ofSeconds(10));
            assertThat(other.getContentBlocks(DataBlock.class)).isEmpty();
            ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
            verify(transport, times(3)).stream(requests.capture());
            assertThat(requests.getAllValues().get(1).getBody()).contains(IMAGE_URL);
            assertThat(requests.getAllValues().getLast().getBody()).doesNotContain(IMAGE_URL);
        }
    }

    @Test
    void keepsImagesFromToolCallingRoundsInTheNextModelRequest(@TempDir Path directory) {
        HttpTransport transport = mock(HttpTransport.class);
        when(transport.stream(any())).thenReturn(Flux.just(chunk("""
                {"images":[{"type":"image_url","image_url":{"url":"%s"}}],
                "tool_calls":[{"index":0,"id":"tool-1","type":"function",
                "function":{"name":"inspect","arguments":"{}"}}]}
                """.formatted(IMAGE_URL), "tool_calls"), "[DONE]"),
                Flux.just(chunk("{\"content\":\"检查完成\"}", "stop").replace("reply", "reply-2"), "[DONE]"));
        AgentTool tool = mock(AgentTool.class);
        when(tool.getName()).thenReturn("inspect");
        when(tool.getDescription()).thenReturn("检查产物");
        when(tool.getParameters()).thenReturn(Map.of("type", "object", "properties", Map.of()));
        when(tool.callAsync(any())).thenReturn(Mono.just(ToolResultBlock.text("检查通过")));
        Toolkit toolkit = new Toolkit();
        toolkit.registerAgentTool(tool);
        JsonFileAgentStateStore stateStore = new JsonFileAgentStateStore(directory);
        try (ReActAgent agent = ReActAgent.builder().name("image-test").model(model(transport, true, true))
                    .toolkit(toolkit).stateStore(stateStore)
                    .permissionContext(PermissionContextState.builder().mode(PermissionMode.BYPASS).build())
                    .middleware(new MultimodalResponseMiddleware()).build()) {

            Msg result = agent.call("生成图片并检查", RuntimeContext.builder()
                    .userId("user-1").sessionId("session-1").build()).block(Duration.ofSeconds(10));

            assertThat(result.getTextContent()).isEqualTo("检查完成");
            assertThat(result.getContentBlocks(DataBlock.class)).isEmpty();
            verify(tool).callAsync(any());
            ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
            verify(transport, times(2)).stream(requests.capture());
            assertThat(requests.getAllValues().getLast().getBody()).contains(IMAGE_URL);
            AgentState saved = stateStore.get("user-1", "session-1", "agent_state", AgentState.class).orElseThrow();
            assertThat(saved.getContext().stream().filter(message -> message.hasContentBlocks(ToolUseBlock.class))
                    .findFirst().orElseThrow().getContentBlocks(DataBlock.class)).hasSize(1);
        }
    }

    @Test
    void restoresImageRepliesThroughHarnessRuntimeMiddleware(@TempDir Path directory) {
        HttpTransport transport = mock(HttpTransport.class);
        when(transport.stream(any())).thenReturn(Flux.just(chunk("""
                {"images":[{"type":"image_url","image_url":{"url":"%s"}}]}
                """.formatted(IMAGE_URL), "stop"), "[DONE]"));
        try (HarnessAgent agent = HarnessAgent.builder().name("harness-image-test").model(model(transport, true, true))
                    .workspace(directory.resolve("workspace"))
                    .distributedStore(DistributedStore.builder()
                            .agentStateStore(new JsonFileAgentStateStore(directory.resolve("state")))
                            .baseStore(new InMemoryStore()).build())
                    .disableFilesystemTools().disableShellTool().disableSubagents().disableDynamicSkills()
                    .disableDefaultWorkspaceSkills().disableWorkspaceContext().disableAtPathExpansion()
                    .disableMemoryHooks().disableMemoryTools().disableCompaction().disableToolResultEviction()
                    .disableToolsConfig().middleware(new MultimodalResponseMiddleware()).build()) {

            Msg result = agent.call("画图", RuntimeContext.builder()
                    .userId("user-1").sessionId("session-1").build()).block(Duration.ofSeconds(10));

            assertThat(result.getTextContent()).isEmpty();
            assertThat(result.getContentBlocks(DataBlock.class)).hasSize(1);
            agent.clearStateCache();
            assertThat(agent.getDelegate().getAgentState("user-1", "session-1").getContext().getLast()
                    .getContentBlocks(DataBlock.class)).hasSize(1);
        }
    }

    @Test
    void parsesMixedContentArraysInNonStreamingCompletions() {
        HttpTransport transport = mock(HttpTransport.class);
        when(transport.execute(any())).thenReturn(HttpResponse.builder().statusCode(200).body("""
                {"id":"reply","object":"chat.completion","choices":[{"index":0,"message":{"role":"assistant",
                "content":[{"type":"text","text":"图片"},{"type":"image_url","image_url":{"url":"%s"}}]},
                "finish_reason":"stop"}]}
                """.formatted(IMAGE_URL)).build());

        ChatResponse result = model(transport, false, true)
                .stream(List.of(new UserMessage("draw")), List.of(), null).blockLast();

        assertThat(result.getContent()).hasSize(2);
        assertThat(((TextBlock) result.getContent().getFirst()).getText()).isEqualTo("图片");
        assertThat(result.getContent().getLast()).isInstanceOf(DataBlock.class);
    }

    @Test
    void removesHistoricalImageInputsWhenSwitchingToTextOnlyModel() {
        HttpTransport transport = mock(HttpTransport.class);
        when(transport.stream(any())).thenReturn(Flux.just(chunk("{\"content\":\"ok\"}", "stop"), "[DONE]"));
        UserMessage input = new UserMessage(TextBlock.builder().text("仍然保留文字").build(), DataBlock.builder()
                .source(Base64Source.builder().mediaType("image/png").data("aW1hZ2U=").build()).build());

        model(transport, true, false).stream(List.of(input), List.of(), null).collectList().block();

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(transport).stream(request.capture());
        assertThat(request.getValue().getBody()).contains("仍然保留文字").doesNotContain("image_url", "base64");
    }

    private OpenAIChatModel model(HttpTransport transport, boolean streaming, boolean imageInput) {
        return OpenAIChatModel.builder().apiKey("test-key").modelName("test-model").stream(streaming)
                .formatter(new MultimodalOpenAIFormatter(imageInput))
                .httpTransport(new MultimodalOpenAITransportDecorator(transport)).build();
    }

    private String chunk(String delta, String reason) {
        return "{\"id\":\"reply\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":"
                + delta + ",\"finish_reason\":" + (reason == null ? "null" : "\"" + reason + "\"") + "}]}";
    }
}
