package com.x.ai.converter;

import com.x.ai.common.domain.SkillDo;
import com.x.ai.common.model.ResourceEnablement;
import io.agentscope.core.skill.AgentSkill;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SkillConverter {

    /**
     * 所有管理入口复用当前用户启用快照，避免写操作返回默认启用状态。
     */
    public static SkillDo toSkillDo(AgentSkill skill, ResourceEnablement enablement) {
        return new SkillDo()
                .setName(skill.getName())
                .setDescription(skill.getDescription())
                .setContent(skill.getSkillContent())
                .setEnabled(enablement.isSkillEnabled(skill.getName()))
                .setResourcePaths(skill.getResourcePaths().stream().sorted().toList());
    }
}
