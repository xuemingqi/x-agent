package com.x.ai.app.service;

import com.x.ai.common.model.AgentAppModelConfig;

public interface AgentAppModelService {

    /**
     * 按应用声明的提示词和执行限制，将结果类型转换为 Schema，反序列化并校验模型输出。
     * 每次调用隔离用户、应用和会话；未指定模型时使用当前用户默认模型。
     */
    <T> T generate(String appId, Long modelId, String input, AgentAppModelConfig<T> configuration);
}
