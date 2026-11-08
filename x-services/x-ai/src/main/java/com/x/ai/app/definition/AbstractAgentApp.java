package com.x.ai.app.definition;

import com.x.ai.app.service.AgentAppModelService;
import com.x.ai.common.domain.AgentAppDo;
import com.x.ai.common.model.AgentAppModelConfig;

import java.time.Duration;

/**
 * 智能应用的统一基类：声明应用身份，绑定独立的结构化模型执行入口。
 * 子类实现应用定义，父类统一组装配置并执行；T 为应用默认操作的结果类型。
 */
public abstract class AbstractAgentApp<T> {

    private final AgentAppModelService modelService;


    protected AbstractAgentApp(AgentAppModelService modelService) {
        this.modelService = modelService;
    }

    /**
     * 返回代码维护的应用定义
     */
    public abstract AgentAppDo definition();

    /**
     * 应用默认操作的系统提示词
     */
    protected abstract String systemPrompt();

    /**
     * 应用默认操作的结构化结果类型
     */
    protected abstract Class<T> resultType();

    /**
     * 默认最多执行三次推理迭代
     */
    protected int maxIterations() {
        return 3;
    }

    /**
     * 默认等待两分钟，子类可按应用需要覆盖。
     */
    protected Duration timeout() {
        return Duration.ofMinutes(2);
    }

    /**
     * 使用子类声明的规则执行默认操作
     */
    public final T generate(Long modelId, String input) {
        AgentAppModelConfig<T> configuration = AgentAppModelConfig.<T>builder()
                .systemPrompt(systemPrompt())
                .resultType(resultType())
                .maxIterations(maxIterations())
                .timeout(timeout())
                .build();
        return generate(modelId, input, configuration);
    }

    /**
     * 额外操作可由子类声明不同的结果类型和配置
     */
    protected final <R> R generate(Long modelId, String input, AgentAppModelConfig<R> configuration) {
        return modelService.generate(definition().getId(), modelId, input, configuration);
    }
}
