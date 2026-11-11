package com.x.ai.service;

import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.common.model.SkillRuntimeSnapshot;

public interface SkillRuntimeService {
    /**
     * 按用户启用状态读取自己拥有的 Skill 快照。
     */
    SkillRuntimeSnapshot load(String userId, ResourceEnablement enablement);
}
