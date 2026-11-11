package com.x.ai.common.model;

import com.x.ai.common.domain.TokenUsageDo;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.model.ChatUsage;

import java.util.HashSet;
import java.util.Set;

/**
 * 单次执行的模型调用用量，按事件 ID 去重并支持并行子 Agent 更新。
 */
public class TokenUsageAccumulator {

    /**
     * 已统计的模型调用结束事件，转发时保留 ID 以防重复统计。
     */
    private final Set<String> modelCallIds = new HashSet<>();

    /**
     * 当前累计值；对外只返回快照，避免历史值随后续调用变化。
     */
    private final TokenUsageDo usage = new TokenUsageDo();

    /**
     * 累加一次提供方已报告的模型调用，忽略缺失用量及重复事件。
     */
    public synchronized void record(ModelCallEndEvent event) {
        ChatUsage value = event.getUsage();
        if (value == null || !modelCallIds.add(event.getId())) {
            return;
        }
        usage.setInputTokens(usage.getInputTokens() + value.getInputTokens())
                .setOutputTokens(usage.getOutputTokens() + value.getOutputTokens())
                .setTotalTokens(usage.getTotalTokens() + (long) value.getInputTokens() + value.getOutputTokens())
                .setCachedTokens(usage.getCachedTokens() + value.getCachedTokens())
                .setCacheCreationTokens(usage.getCacheCreationTokens() + value.getCacheCreationTokens())
                .setReasoningTokens(usage.getReasoningTokens() + value.getReasoningTokens())
                .setToolUsePromptTokens(usage.getToolUsePromptTokens() + value.getToolUsePromptTokens())
                .setModelCalls(usage.getModelCalls() + 1)
                .setTimeSeconds(usage.getTimeSeconds() + value.getTime());
    }

    /**
     * 返回当前累计用量的独立快照。
     */
    public synchronized TokenUsageDo snapshot() {
        return new TokenUsageDo()
                .setInputTokens(usage.getInputTokens()).setOutputTokens(usage.getOutputTokens())
                .setTotalTokens(usage.getTotalTokens()).setCachedTokens(usage.getCachedTokens())
                .setCacheCreationTokens(usage.getCacheCreationTokens()).setReasoningTokens(usage.getReasoningTokens())
                .setToolUsePromptTokens(usage.getToolUsePromptTokens()).setModelCalls(usage.getModelCalls())
                .setTimeSeconds(usage.getTimeSeconds());
    }
}
