package com.x.ai.common.model;

import com.x.ai.common.domain.TokenUsageDo;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.model.ChatUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUsageAccumulatorTest {

    @Test
    void sumsModelRoundsWithoutAddingTokenSubsetsToTotalsOrCountingForwardedEventsTwice() {
        TokenUsageAccumulator accumulator = new TokenUsageAccumulator();
        ModelCallEndEvent first = new ModelCallEndEvent("reply-1", ChatUsage.builder()
                .inputTokens(100).outputTokens(40).cachedTokens(30).cacheCreationTokens(10)
                .reasoningTokens(20).toolUsePromptTokens(15).time(0.5).build());
        accumulator.record(first);
        TokenUsageDo previous = accumulator.snapshot();
        accumulator.record(first);
        accumulator.record(new ModelCallEndEvent("reply-2", new ChatUsage(50, 10, 0.25)));

        TokenUsageDo usage = accumulator.snapshot();
        assertThat(usage.getInputTokens()).isEqualTo(150);
        assertThat(usage.getOutputTokens()).isEqualTo(50);
        assertThat(usage.getTotalTokens()).isEqualTo(200);
        assertThat(usage.getModelCalls()).isEqualTo(2);
        assertThat(usage.getCachedTokens()).isEqualTo(30);
        assertThat(usage.getCacheCreationTokens()).isEqualTo(10);
        assertThat(usage.getReasoningTokens()).isEqualTo(20);
        assertThat(usage.getToolUsePromptTokens()).isEqualTo(15);
        assertThat(usage.getTimeSeconds()).isEqualTo(0.75);
        assertThat(previous.getTotalTokens()).isEqualTo(140);
    }

    @Test
    void ignoresMissingProviderUsageAndKeepsExecutionsIndependent() {
        TokenUsageAccumulator first = new TokenUsageAccumulator();
        TokenUsageAccumulator second = new TokenUsageAccumulator();
        first.record(new ModelCallEndEvent("reply-1", null));
        second.record(new ModelCallEndEvent("reply-2", new ChatUsage(10, 5, 1)));

        assertThat(first.snapshot().getModelCalls()).isZero();
        assertThat(first.snapshot().getTotalTokens()).isZero();
        assertThat(second.snapshot().getTotalTokens()).isEqualTo(15);
    }
}
