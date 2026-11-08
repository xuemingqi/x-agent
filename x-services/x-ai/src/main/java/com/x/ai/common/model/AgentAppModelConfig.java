package com.x.ai.common.model;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;

/**
 * 应用代码声明的单次模型调用规则，不可变且不作为前端请求参数。
 */
@Value
@Builder
public class AgentAppModelConfig<T> {

    /**
     * 由应用定义的系统提示词
     */
    private final String systemPrompt;

    /**
     * 本次操作的结构化结果类型，同时用于生成 Schema 和反序列化。
     */
    private final Class<T> resultType;

    /**
     * 本次 Agent 调用允许的最大推理迭代次数。
     */
    private final int maxIterations;

    /**
     * 等待本次 Agent 调用完成的最长时间。
     */
    private final Duration timeout;
}
