package com.x.ai.common.model;

import com.x.ai.common.enums.SkillScope;
import lombok.Value;

import java.util.Set;

/**
 * 当前用户的扩展启用快照；未关闭的资源默认启用，作用域相同才共享 Skill 开关。
 */
@Value
public class ResourceEnablement {

    /**
     * 当前用户关闭的资源键集合，格式为 SKILL:作用域:名称或 MCP:配置 ID。
     */
    private Set<String> disabledKeys;

    public ResourceEnablement(Set<String> disabledKeys) {
        this.disabledKeys = Set.copyOf(disabledKeys);
    }

    public boolean isSkillEnabled(SkillScope scope, String name) {
        return !disabledKeys.contains(skillKey(scope, name));
    }

    public boolean isMcpEnabled(Long id) {
        return !disabledKeys.contains(mcpKey(id));
    }

    public static String skillKey(SkillScope scope, String name) {
        return "SKILL:" + scope.name() + ":" + name;
    }

    public static String mcpKey(Long id) {
        return "MCP:" + id;
    }
}
