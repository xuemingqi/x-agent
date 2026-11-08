package com.x.ai.service;

import com.x.ai.common.model.AiModelRuntimeConfig;

public interface AiModelRuntimeService {

    /**
     * 校验模型归属与可用性；未指定模型时只使用该用户自己的默认模型。
     * 显式用户参数供聊天异步执行、定时任务、Skill 导入及 App 共用。
     *
     * @param userId 可信的当前用户 ID
     * @param modelId 模型配置 ID，为空时选择个人默认模型
     * @return 已授权的模型运行配置
     */
    AiModelRuntimeConfig requireRuntimeModel(String userId, Long modelId);
}
