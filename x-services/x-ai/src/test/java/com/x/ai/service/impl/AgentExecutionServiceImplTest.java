package com.x.ai.service.impl;

import com.x.ai.common.domain.AgentResponseDo;
import com.x.ai.common.domain.TokenUsageDo;
import com.x.ai.common.model.TokenUsageAccumulator;
import com.x.ai.common.dto.AgentRequestDto;
import com.x.ai.common.enums.AgentEventType;
import com.x.ai.common.model.AiModelRuntimeConfig;
import com.x.ai.common.model.UserContext;
import com.x.ai.db.enums.AgentModelMultimodalCapability;
import com.x.ai.properties.AgentProperties;
import com.x.ai.properties.BrowserScreenshotProperties;
import com.x.ai.service.AiModelRuntimeService;
import com.x.ai.service.BrowserScreenshotService;
import com.x.ai.service.ConversationFileService;
import com.x.ai.service.ConversationService;
import com.x.ai.service.HarnessAgentService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentExecutionServiceImplTest {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);

    private final HarnessAgentService agents = mock(HarnessAgentService.class);

    private final AiModelRuntimeService models = mock(AiModelRuntimeService.class);

    private final ActiveTaskRegistryServiceImpl tasks = spy(new ActiveTaskRegistryServiceImpl());

    private final ConversationService conversations = mock(ConversationService.class);

    private final ConversationFileService files = mock(ConversationFileService.class);

    private final BrowserScreenshotService screenshots = mock(BrowserScreenshotService.class);

    private final BrowserScreenshotProperties screenshotProperties = new BrowserScreenshotProperties();

    private final HarnessAgent agent = mock(HarnessAgent.class);

    private final ReActAgent delegate = mock(ReActAgent.class);

    private final AiModelRuntimeConfig model = new AiModelRuntimeConfig()
            .setModelId(7L).setMultimodalCapability(AgentModelMultimodalCapability.NONE);

    private final UserContext user = new UserContext().setUserId("user-1");

    private final AgentRequestDto request = new AgentRequestDto()
            .setModelId(7L).setSessionId("session-1").setRequest("hello").setAttachmentIds(List.of());

    private final AgentExecutionServiceImpl service = new AgentExecutionServiceImpl(agents, models, tasks,
            new AgentProperties(), conversations, new ConversationTurnRecordServiceImpl(conversations),
            files, screenshots, screenshotProperties);

    @BeforeEach
    void setUp() {
        screenshotProperties.setHeartbeatIntervalSeconds(1);
        when(conversations.appendUserMessage("user-1", "session-1", "hello")).thenReturn(10L);
        when(files.prepareInputs("user-1", "session-1", List.of(), 10L)).thenReturn(List.of());
        when(models.requireRuntimeModel("user-1", 7L)).thenReturn(model);
        when(agents.getAgent(model, "user-1")).thenReturn(agent);
        when(agent.getDelegate()).thenReturn(delegate);
        when(agents.executeStream(eq(agent), any(), any())).thenReturn(Flux.just(
                new TextBlockDeltaEvent("reply-1", "text-1", "hello"), new AgentEndEvent("reply-1")));
    }

    @Test
    void emitsCumulativeUsageAcrossModelCallsAndIgnoresRepeatedForwardedEvents() {
        ModelCallEndEvent child = new ModelCallEndEvent("child", new ChatUsage(100, 20, 0.2));
        child.withSource("main/researcher");
        when(agents.executeStream(eq(agent), any(), any())).thenReturn(Flux.just(child, child,
                new ModelCallEndEvent("main", ChatUsage.builder().inputTokens(50).outputTokens(10)
                        .reasoningTokens(5).cachedTokens(30).build()), new AgentEndEvent("main")));

        List<AgentResponseDo> results = service.streamRun(user, request).collectList().block(TEST_TIMEOUT);
        List<TokenUsageDo> snapshots = results.stream()
                .filter(result -> result.getEventType() == AgentEventType.TOKEN_USAGE)
                .map(result -> (TokenUsageDo) result.getStructuredData().get("usage")).toList();

        assertThat(snapshots).extracting(TokenUsageDo::getTotalTokens).containsExactly(120L, 120L, 180L);
        assertThat(snapshots.getLast().getModelCalls()).isEqualTo(2);
        assertThat(snapshots.getLast().getReasoningTokens()).isEqualTo(5);
    }

    @Test
    void blockingResponsesIncludeUsageFromEveryModelCall() {
        when(agents.executeBlocking(eq(agent), any(), any())).thenAnswer(invocation -> {
            RuntimeContext context = invocation.getArgument(2);
            TokenUsageAccumulator usage = context.get(TokenUsageAccumulator.class);
            usage.record(new ModelCallEndEvent("first", new ChatUsage(100, 20, 0.2)));
            usage.record(new ModelCallEndEvent("second", new ChatUsage(50, 10, 0.1)));
            return Mono.just(new AssistantMessage("最终答案"));
        });

        AgentResponseDo result = service.runStandalone(user, request);

        TokenUsageDo usage = (TokenUsageDo) result.getStructuredData().get("usage");
        assertThat(usage.getTotalTokens()).isEqualTo(180);
        assertThat(usage.getModelCalls()).isEqualTo(2);
    }

    @Test
    void sendsInitialAndPeriodicHeartbeatsWhileInitializationIsBlocked() throws Exception {
        CountDownLatch initializing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Thread> preparationThread = new AtomicReference<>();
        when(agents.getAgent(model, "user-1")).thenAnswer(invocation -> {
            preparationThread.set(Thread.currentThread());
            initializing.countDown();
            assertThat(release.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            return agent;
        });
        BlockingQueue<AgentResponseDo> events = new LinkedBlockingQueue<>();
        Disposable subscription = service.streamRun(user, request).subscribe(events::add, error::set, completed::countDown);
        try {
            AgentResponseDo initial = nextEvent(events);
            assertThat(initial.getEventType()).isEqualTo(AgentEventType.HEARTBEAT);
            assertThat(initializing.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            assertThat(preparationThread.get()).isNotSameAs(Thread.currentThread());
            AgentResponseDo periodic = nextEvent(events);
            assertThat(periodic.getEventType()).isEqualTo(AgentEventType.HEARTBEAT);
            assertThat(periodic.getTaskId()).isEqualTo(initial.getTaskId());
            verify(agents, never()).executeStream(any(), any(), any());
            verify(tasks, never()).register(anyString(), anyString(), any());

            release.countDown();
            assertThat(completed.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            assertThat(error.get()).isNull();
            List<AgentResponseDo> executionEvents = events.stream()
                    .filter(event -> event.getEventType() != AgentEventType.HEARTBEAT).toList();
            assertThat(executionEvents).extracting(AgentResponseDo::getEventType).containsExactly(
                    AgentEventType.TASK_STARTED, AgentEventType.TEXT_DELTA,
                    AgentEventType.AGENT_END, AgentEventType.TASK_END);
            assertThat(executionEvents).allSatisfy(event -> assertThat(event.getTaskId()).isEqualTo(initial.getTaskId()));
            assertThat(tasks.interrupt(initial.getTaskId(), "user-1")).isFalse();
        } finally {
            release.countDown();
            subscription.dispose();
        }
    }

    @Test
    void cancellationInterruptsPreparationWithoutStartingTheModel() throws Exception {
        CountDownLatch initializing = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        when(agents.getAgent(model, "user-1")).thenAnswer(invocation -> {
            initializing.countDown();
            try {
                return Mono.<HarnessAgent>never().block();
            } finally {
                exited.countDown();
            }
        });
        BlockingQueue<AgentResponseDo> events = new LinkedBlockingQueue<>();
        Disposable subscription = service.streamRun(user, request).subscribe(events::add);
        try {
            AgentResponseDo initial = nextEvent(events);
            assertThat(initializing.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            subscription.dispose();
            assertThat(exited.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            verify(agents, never()).executeStream(any(), any(), any());
            verify(tasks, never()).register(anyString(), anyString(), any());
            verify(screenshots).cancelTask(initial.getTaskId());
            verify(agent, never()).close();
        } finally {
            subscription.dispose();
        }
    }

    @Test
    void cancellationAtInitialHeartbeatDoesNotStartPreparation() {
        List<AgentResponseDo> events = service.streamRun(user, request).take(1).collectList().block(TEST_TIMEOUT);

        assertThat(events).extracting(AgentResponseDo::getEventType).containsExactly(AgentEventType.HEARTBEAT);
        verifyNoInteractions(conversations, files, models, agents, tasks);
    }

    @Test
    void rejectedModelNeverAnnouncesTaskStartOrRunsAnAgent() {
        XException error = new XException(ResponseCodeEnum.AGENT_MODEL_UNAVAILABLE);
        when(models.requireRuntimeModel("user-1", 7L)).thenThrow(error);
        BlockingQueue<AgentResponseDo> events = new LinkedBlockingQueue<>();

        assertThatThrownBy(() -> service.streamRun(user, request).doOnNext(events::add).blockLast(TEST_TIMEOUT))
                .isSameAs(error);

        assertThat(events).allSatisfy(event -> assertThat(event.getEventType()).isEqualTo(AgentEventType.HEARTBEAT));
        verifyNoInteractions(agents, tasks);
    }

    @Test
    void streamConstructionFailureStillUnregistersTheTask() {
        IllegalStateException error = new IllegalStateException("cannot construct stream");
        when(agents.executeStream(eq(agent), any(), any())).thenThrow(error);
        BlockingQueue<AgentResponseDo> events = new LinkedBlockingQueue<>();

        assertThatThrownBy(() -> service.streamRun(user, request).doOnNext(events::add).blockLast(TEST_TIMEOUT))
                .isSameAs(error);

        String taskId = events.element().getTaskId();
        verify(tasks).register(eq(taskId), eq("user-1"), any());
        assertThat(tasks.interrupt(taskId, "user-1")).isFalse();
    }

    @Test
    void disconnectingAnActiveStreamInterruptsOnlyItsExecutionAndUnregistersIt() throws Exception {
        when(agents.executeStream(eq(agent), any(), any())).thenReturn(Flux.never());
        BlockingQueue<AgentResponseDo> events = new LinkedBlockingQueue<>();
        Disposable subscription = service.streamRun(user, request).subscribe(events::add);
        try {
            AgentResponseDo initial = nextEvent(events);
            AgentResponseDo started = nextEvent(events);
            assertThat(started.getEventType()).isEqualTo(AgentEventType.TASK_STARTED);
            subscription.dispose();

            verify(delegate).interrupt("user-1", "session-1");
            assertThat(tasks.interrupt(initial.getTaskId(), "user-1")).isFalse();
            verify(agent, never()).close();
        } finally {
            subscription.dispose();
        }
    }

    @Test
    void requestedInterruptionKeepsTheStreamOpenUntilArchivingAndTaskEnd() throws Exception {
        Sinks.Many<AgentEvent> source = Sinks.many().unicast().onBackpressureBuffer();
        when(agents.executeStream(eq(agent), any(), any())).thenReturn(source.asFlux());
        BlockingQueue<AgentResponseDo> events = new LinkedBlockingQueue<>();
        CountDownLatch completed = new CountDownLatch(1);
        Disposable subscription = service.streamRun(user, request).subscribe(events::add,
                error -> { throw new AssertionError(error); }, completed::countDown);
        try {
            AgentResponseDo initial = nextEvent(events);
            assertThat(nextEvent(events).getEventType()).isEqualTo(AgentEventType.TASK_STARTED);
            assertThat(tasks.interrupt(initial.getTaskId(), "user-1")).isTrue();
            verify(delegate).interrupt("user-1", "session-1");
            assertThat(completed.getCount()).isEqualTo(1);

            source.tryEmitNext(new TextBlockDeltaEvent("reply-1", "text-1", "partial"));
            source.tryEmitNext(new AgentEndEvent("reply-1"));
            source.tryEmitComplete();
            assertThat(completed.await(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            verify(conversations).appendMessages(eq("user-1"), eq("session-1"), eq(10L), any());
            assertThat(events.stream().filter(event -> event.getEventType() != AgentEventType.HEARTBEAT).toList())
                    .extracting(AgentResponseDo::getEventType).containsExactly(
                            AgentEventType.TEXT_DELTA, AgentEventType.AGENT_END, AgentEventType.TASK_END);
            assertThat(tasks.interrupt(initial.getTaskId(), "user-1")).isFalse();
        } finally {
            subscription.dispose();
        }
    }

    @Test
    void eachSubscriptionGetsItsOwnTaskIdAndPreservesExplicitUserContext() {
        Flux<AgentResponseDo> stream = service.streamRun(user, request);
        verifyNoInteractions(conversations, agents, models);

        List<AgentResponseDo> first = stream.collectList().block(TEST_TIMEOUT);
        List<AgentResponseDo> second = stream.collectList().block(TEST_TIMEOUT);

        assertThat(first.getFirst().getTaskId()).isNotEqualTo(second.getFirst().getTaskId());
        assertThat(first).allSatisfy(event -> {
            assertThat(event.getTaskId()).isEqualTo(first.getFirst().getTaskId());
            assertThat(event.getUserId()).isEqualTo("user-1");
            assertThat(event.getSessionId()).isEqualTo("session-1");
        });
        ArgumentCaptor<RuntimeContext> contexts = ArgumentCaptor.forClass(RuntimeContext.class);
        verify(agents, times(2)).executeStream(eq(agent), any(), contexts.capture());
        assertThat(contexts.getAllValues()).allSatisfy(context -> {
            assertThat(context.getUserId()).isEqualTo("user-1");
            assertThat(context.getSessionId()).isEqualTo("session-1");
        });
    }

    /**
     * 使用事件信号等待代替休眠，使初始化、周期心跳和结束顺序可以直接断言。
     */
    private AgentResponseDo nextEvent(BlockingQueue<AgentResponseDo> events) throws InterruptedException {
        AgentResponseDo event = events.poll(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertThat(event).isNotNull();
        return event;
    }
}
