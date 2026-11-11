package com.x.ai.service.impl;

import com.x.ai.common.domain.ConversationMessageDo;
import com.x.ai.db.enums.ConversationMessageRole;
import com.x.ai.service.ConversationService;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConversationTurnRecordServiceTest {

    @Test
    void archivesAccumulatedUsageOnTheFinalMessageIncludingSubagentCalls() {
        ConversationService conversations = mock(ConversationService.class);
        ConversationTurnRecordServiceImpl service = new ConversationTurnRecordServiceImpl(conversations);
        ModelCallEndEvent child = new ModelCallEndEvent("child-reply", new ChatUsage(100, 20, 0.2));
        child.withSource("main/researcher");

        service.record("user-1", "session-1", 10L, Flux.just(
                child, child,
                new ModelCallEndEvent("main-reply", ChatUsage.builder().inputTokens(50).outputTokens(10)
                        .reasoningTokens(5).cachedTokens(30).build()),
                new AgentResultEvent(new AssistantMessage("最终答案")), new AgentEndEvent("main-reply"))).blockLast();

        ArgumentCaptor<List<ConversationMessageDo>> messages = ArgumentCaptor.captor();
        verify(conversations).appendMessages(eq("user-1"), eq("session-1"), eq(10L), messages.capture());
        assertThat(messages.getValue()).hasSize(1);
        assertThat(messages.getValue().getFirst().getUsage().getTotalTokens()).isEqualTo(180);
        assertThat(messages.getValue().getFirst().getUsage().getModelCalls()).isEqualTo(2);
        assertThat(messages.getValue().getFirst().getUsage().getReasoningTokens()).isEqualTo(5);
    }

    @Test
    void archivesPartialOutputWithoutTheSdkInterruptionGreeting() {
        ConversationService conversations = mock(ConversationService.class);
        ConversationTurnRecordServiceImpl service = new ConversationTurnRecordServiceImpl(conversations);
        AssistantMessage interrupted = AssistantMessage.builder().generateReason(GenerateReason.INTERRUPTED)
                .content(TextBlock.builder().text("I noticed that you have interrupted me. What can I do for you?").build()).build();

        service.record("user-1", "session-1", 10L, Flux.just(
                new TextBlockDeltaEvent("reply-1", "text-1", "已经完成的布局"),
                new AgentResultEvent(interrupted), new AgentEndEvent("reply-1"))).blockLast();

        ArgumentCaptor<List<ConversationMessageDo>> messages = ArgumentCaptor.captor();
        verify(conversations).appendMessages(eq("user-1"), eq("session-1"), eq(10L), messages.capture());
        assertThat(messages.getValue()).extracting(ConversationMessageDo::getContent).containsExactly("已经完成的布局");
    }

    @Test
    void keepsAnAssistantHistoryEntryForImageOnlyReplies() {
        ConversationService conversations = mock(ConversationService.class);
        ConversationTurnRecordServiceImpl service = new ConversationTurnRecordServiceImpl(conversations);
        AssistantMessage reply = new AssistantMessage(DataBlock.builder().source(URLSource.builder()
                .url("data:image/png;base64,aW1hZ2U=").mimeType("image/png").build()).build());

        service.record("user-1", "session-1", 10L, Flux.just(new AgentResultEvent(reply))).blockLast();

        ArgumentCaptor<List<ConversationMessageDo>> messages = ArgumentCaptor.captor();
        verify(conversations).appendMessages(eq("user-1"), eq("session-1"), eq(10L), messages.capture());
        assertThat(messages.getValue()).hasSize(1);
        assertThat(messages.getValue().getFirst().getRole()).isEqualTo(ConversationMessageRole.ASSISTANT);
        assertThat(messages.getValue().getFirst().getContent()).isEmpty();
    }
}
