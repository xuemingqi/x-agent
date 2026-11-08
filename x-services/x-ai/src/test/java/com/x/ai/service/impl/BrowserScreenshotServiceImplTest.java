package com.x.ai.service.impl;

import com.x.ai.common.domain.ConversationFileDo;
import com.x.ai.common.dto.BrowserScreenshotResultDto;
import com.x.ai.common.enums.AgentEventType;
import com.x.ai.common.model.BrowserScreenshotContext;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.ai.converter.MultimodalOpenAIFormatter;
import com.x.ai.db.enums.AgentConversationFileKind;
import com.x.framework.common.exception.XException;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.ai.properties.BrowserScreenshotProperties;
import com.x.ai.service.ConversationFileService;
import com.x.ai.tool.BrowserScreenshotTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventEmitter;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.transport.HttpRequest;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrowserScreenshotServiceImplTest {

    @AfterEach
    void clearIdentity() {
        UserThreadLocalUtil.clear();
    }

    private final ConversationFileService files = mock(ConversationFileService.class);

    private final BrowserScreenshotProperties properties = new BrowserScreenshotProperties();

    private final BrowserScreenshotServiceImpl service = new BrowserScreenshotServiceImpl(files, properties);

    private final List<AgentEvent> events = new ArrayList<>();

    private final RuntimeContext context = RuntimeContext.builder().userId("user-1").sessionId("session-1")
            .put(BrowserScreenshotContext.class, new BrowserScreenshotContext().setTaskId("task-1").setMessageId(10L))
            .build();

    @BeforeEach
    void prepareFiles() {
        UserThreadLocalUtil.set(UserInfo.builder().userId("user-1").build());
        when(files.listPublishedOutputs("user-1", "session-1", 10L)).thenReturn(List.of(new ConversationFileDo()
                .setId("20").setName("page.html").setSessionId("session-1").setKind(AgentConversationFileKind.OUTPUT)));
        ConversationFileDo screenshot = new ConversationFileDo().setId("30").setSessionId("session-1")
                .setName("screenshot.png").setContentType("image/png").setKind(AgentConversationFileKind.INPUT);
        when(files.getFile("user-1", "session-1", 30L)).thenReturn(screenshot);
        when(files.getFile("user-1", "session-1", 999L))
                .thenThrow(new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_NOT_FOUND));
        when(files.readInputImages("user-1", "session-1", List.of(screenshot))).thenReturn(List.of(DataBlock.builder()
                .source(Base64Source.builder().mediaType("image/png").data("aW1hZ2U=").build()).build()));
        when(files.prepareInputs("user-1", "session-1", List.of(30L), 10L)).thenReturn(List.of(screenshot));
    }

    @Test
    void waitsForConsentThenReturnsImageAndRejectsDuplicateReplies() throws Exception {
        CompletableFuture<ToolResultBlock> result = request();
        String requestId = requestId();
        assertThat(result).isNotDone();
        verify(files, never()).prepareInputs(any(), any(), any(), any());

        service.complete("task-1", requestId, new BrowserScreenshotResultDto().setAccepted(true).setFileId(30L));

        assertThat(result.get(1, TimeUnit.SECONDS).getOutput()).anyMatch(DataBlock.class::isInstance);
        assertThat(((CustomEvent) events.getLast()).getValue()).containsEntry("status", "received");
        verify(files).prepareInputs("user-1", "session-1", List.of(30L), 10L);
        var fileOperations = inOrder(files);
        fileOperations.verify(files).prepareInputs("user-1", "session-1", List.of(30L), 10L);
        fileOperations.verify(files).readInputImages(any(), any(), any());
        assertThatThrownBy(() -> service.complete("task-1", requestId,
                new BrowserScreenshotResultDto().setAccepted(true).setFileId(30L))).isInstanceOf(XException.class);
    }

    @Test
    void rejectsAnotherUserTaskOrAttachmentWithoutConsumingRequest() throws Exception {
        CompletableFuture<ToolResultBlock> result = request();
        String requestId = requestId();
        BrowserScreenshotResultDto accepted = new BrowserScreenshotResultDto().setAccepted(true).setFileId(30L);
        UserThreadLocalUtil.set(UserInfo.builder().userId("other-user").build());
        assertThatThrownBy(() -> service.complete("task-1", requestId, accepted)).isInstanceOf(XException.class);
        UserThreadLocalUtil.set(UserInfo.builder().userId("user-1").build());
        assertThatThrownBy(() -> service.complete("other-task", requestId, accepted)).isInstanceOf(XException.class);
        assertThatThrownBy(() -> service.complete("task-1", requestId, accepted.setFileId(999L)))
                .isInstanceOf(XException.class);
        assertThat(result).isNotDone();
        service.complete("task-1", requestId, new BrowserScreenshotResultDto().setAccepted(false));
        assertThat(result.get(1, TimeUnit.SECONDS).getOutput()).allMatch(TextBlock.class::isInstance);
        verify(files, never()).prepareInputs(any(), any(), any(), any());
    }

    @Test
    void timeoutAndTaskCancellationReleaseWaitingTools() throws Exception {
        properties.setRequestTimeoutSeconds(1);
        CompletableFuture<ToolResultBlock> timeout = request();
        assertThat(timeout.get(3, TimeUnit.SECONDS).getOutput()).allMatch(TextBlock.class::isInstance);
        assertThat(((CustomEvent) events.getLast()).getValue()).containsEntry("status", "expired");

        events.clear();
        CompletableFuture<ToolResultBlock> cancelled = request();
        String requestId = requestId();
        service.cancelTask("task-1");
        assertThat(cancelled.get(1, TimeUnit.SECONDS).getOutput()).allMatch(TextBlock.class::isInstance);
        assertThat(((CustomEvent) events.getLast()).getValue()).containsEntry("status", "cancelled");
        assertThatThrownBy(() -> service.complete("task-1", requestId, new BrowserScreenshotResultDto().setAccepted(false)))
                .isInstanceOf(XException.class);
    }

    @Test
    void doesNotRequestScreenshotsWithoutBrowserCapabilityOrPublishedHtml() {
        ToolResultBlock unsupported = service.request("20", "检查布局", RuntimeContext.empty()).block();
        assertThat(((TextBlock) unsupported.getOutput().getFirst()).getText()).contains("不支持");
        ToolResultBlock missing = service.request("999", "检查布局", context)
                .contextWrite(value -> value.put(AgentEventEmitter.CONTEXT_KEY, (AgentEventEmitter) events::add)).block();
        assertThat(((TextBlock) missing.getOutput().getFirst()).getText()).contains("先发布");
        assertThat(events).isEmpty();
    }

    @Test
    void disconnectingTheSubscriberInvalidatesItsPendingRequest() {
        CompletableFuture<ToolResultBlock> result = request();
        String requestId = requestId();
        result.cancel(true);
        assertThatThrownBy(() -> service.complete("task-1", requestId,
                new BrowserScreenshotResultDto().setAccepted(true).setFileId(30L))).isInstanceOf(XException.class);
        verify(files, never()).prepareInputs(any(), any(), any(), any());
    }

    @Test
    void realAgentToolWaitsForCallbackAndSendsScreenshotInNextModelRequest() {
        HttpTransport transport = mock(HttpTransport.class);
        when(transport.stream(any())).thenReturn(Flux.just(chunk("""
                {"tool_calls":[{"index":0,"id":"capture-1","type":"function","function":{
                "name":"request_browser_screenshot","arguments":"{\\"fileId\\":\\"20\\",\\"reason\\":\\"检查布局\\"}"}}]}
                """, "tool_calls"), "[DONE]"), Flux.just(chunk("{\"content\":\"已检查截图\"}", "stop"), "[DONE]"));
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new BrowserScreenshotTools(service));
        ReActAgent agent = ReActAgent.builder().name("screenshot-test").toolkit(toolkit)
                .permissionContext(PermissionContextState.builder().mode(PermissionMode.BYPASS).build())
                .model(OpenAIChatModel.builder().modelName("vision-test").apiKey("test-key")
                        .formatter(new MultimodalOpenAIFormatter(true)).httpTransport(transport).build()).build();
        try {
            List<AgentEvent> received = agent.streamEvents(new UserMessage("检查 HTML 页面"), context)
                    .doOnNext(event -> {
                        if (event instanceof CustomEvent custom
                                && custom.getName().equals(AgentEventType.BROWSER_SCREENSHOT_REQUESTED.getValue())) {
                            acceptScreenshot((String) custom.getValue().get("requestId"));
                        }
                    }).collectList().block(Duration.ofSeconds(10));
            assertThat(received).anyMatch(event -> event instanceof CustomEvent);
            ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
            verify(transport, times(2)).stream(requests.capture());
            assertThat(requests.getAllValues().getLast().getBody()).contains("data:image/png;base64,aW1hZ2U=", "image_url", "capture-1");
        } finally {
            agent.close();
        }
    }

    private CompletableFuture<ToolResultBlock> request() {
        return service.request("20", "检查布局", context)
                .contextWrite(value -> value.put(AgentEventEmitter.CONTEXT_KEY, (AgentEventEmitter) events::add)).toFuture();
    }

    /**
     * 模拟截图回调的独立 HTTP 鉴权上下文，不依赖 Agent 工具执行线程。
     */
    private void acceptScreenshot(String requestId) {
        UserInfo previous = UserThreadLocalUtil.get();
        try {
            UserThreadLocalUtil.set(UserInfo.builder().userId("user-1").build());
            service.complete("task-1", requestId,
                    new BrowserScreenshotResultDto().setAccepted(true).setFileId(30L));
        } finally {
            if (previous == null) {
                UserThreadLocalUtil.clear();
            } else {
                UserThreadLocalUtil.set(previous);
            }
        }
    }

    private String requestId() {
        return (String) ((CustomEvent) events.getFirst()).getValue().get("requestId");
    }

    private String chunk(String delta, String finishReason) {
        return "{\"id\":\"reply\",\"object\":\"chat.completion.chunk\",\"model\":\"vision-test\",\"choices\":[{\"index\":0,"
                + "\"delta\":" + delta + ",\"finish_reason\":\"" + finishReason + "\"}]}";
    }
}
