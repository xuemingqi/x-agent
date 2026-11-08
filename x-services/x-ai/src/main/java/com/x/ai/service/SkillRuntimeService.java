package com.x.ai.service;

import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.common.model.SkillRuntimeSnapshot;

public interface SkillRuntimeService {
    /**
     * 按用户启用状态及入口授权读取 Skill；保留公共、共享、个人的覆盖优先级。
     */
    SkillRuntimeSnapshot load(String userId, ResourceEnablement enablement);
}
