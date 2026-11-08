package com.x.ai.converter;

import com.x.ai.common.constants.AgentConstant;
import com.x.ai.common.domain.SkillDo;
import com.x.ai.common.enums.SkillScope;
import com.x.ai.common.model.ResourceEnablement;
import io.agentscope.core.skill.AgentSkill;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SkillConverter {

    /**
     * 所有管理入口使用同一转换，显式传入编辑权限和用户偏好，避免写操作返回默认启用状态。
     */
    public static SkillDo toSkillDo(AgentSkill skill, SkillScope scope, boolean editable, ResourceEnablement enablement) {
        return new SkillDo()
                .setName(skill.getName())
                .setDescription(skill.getDescription())
                .setContent(skill.getSkillContent())
                .setScope(scope)
                .setEditable(editable)
                .setEnabled(enablement.isSkillEnabled(scope, skill.getName()))
                .setSharedByMobile(scope == SkillScope.SHARED
                        ? (String) skill.getMetadataValue(AgentConstant.SHARED_SKILL_SENDER_MOBILE_METADATA) : null)
                .setResourcePaths(skill.getResourcePaths().stream().sorted().toList());
    }
}
