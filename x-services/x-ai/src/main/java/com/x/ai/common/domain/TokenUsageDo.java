package com.x.ai.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 本轮主 Agent 和子 Agent 的模型用量；细分指标包含在输入或输出总量中。
 */
@Data
@Accessors(chain = true)
@Schema(name = "TokenUsageDo", description = "本轮主 Agent 和子 Agent 的累计模型用量，细分指标包含在输入或输出总量中")
public class TokenUsageDo {

    /**
     * 输入 token 总数，包含缓存和工具结果输入。
     */
    @Schema(name = "inputTokens", description = "输入 token 总数，包含缓存和工具结果输入", minimum = "0")
    private long inputTokens;

    /**
     * 输出 token 总数，包含推理输出。
     */
    @Schema(name = "outputTokens", description = "输出 token 总数，包含推理输出", minimum = "0")
    private long outputTokens;

    /**
     * 输入与输出 token 合计，不重复累加细分指标。
     */
    @Schema(name = "totalTokens", description = "输入与输出 token 合计，不重复累加细分指标", minimum = "0")
    private long totalTokens;

    /**
     * 命中提示词缓存的输入 token；提供方未报告时为 0。
     */
    @Schema(name = "cachedTokens", description = "命中提示词缓存的输入 token，提供方未报告时为 0", minimum = "0")
    private long cachedTokens;

    /**
     * 写入提示词缓存的输入 token；提供方未报告时为 0。
     */
    @Schema(name = "cacheCreationTokens", description = "写入提示词缓存的输入 token，提供方未报告时为 0", minimum = "0")
    private long cacheCreationTokens;

    /**
     * 推理输出 token；提供方未报告时为 0。
     */
    @Schema(name = "reasoningTokens", description = "推理输出 token，提供方未报告时为 0", minimum = "0")
    private long reasoningTokens;

    /**
     * 工具结果占用的输入 token；提供方未报告时为 0。
     */
    @Schema(name = "toolUsePromptTokens", description = "工具结果占用的输入 token，提供方未报告时为 0", minimum = "0")
    private long toolUsePromptTokens;

    /**
     * 已报告用量的模型调用次数。
     */
    @Schema(name = "modelCalls", description = "已报告用量的模型调用次数", minimum = "0")
    private int modelCalls;

    /**
     * 各次模型调用耗时之和，单位秒，不等于并行任务的墙钟耗时。
     */
    @Schema(name = "timeSeconds", description = "各次模型调用耗时之和，单位秒，不等于并行任务的实际耗时", minimum = "0")
    private double timeSeconds;
}
