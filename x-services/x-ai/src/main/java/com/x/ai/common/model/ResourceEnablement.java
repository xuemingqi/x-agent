package com.x.ai.common.model;

import lombok.Value;

import java.util.Set;

/**
 * 当前用户的扩展启用快照；未关闭的资源默认启用。
 */
@Value
public class ResourceEnablement {

    /**
     * 当前用户关闭的资源键集合，格式为 SKILL:名称。
     */
    private Set<String> disabledKeys;

    public ResourceEnablement(Set<String> disabledKeys) {
        this.disabledKeys = Set.copyOf(disabledKeys);
    }

    public boolean isSkillEnabled(String name) {
        return !disabledKeys.contains(skillKey(name));
    }

    public static String skillKey(String name) {
        return "SKILL:" + name;
    }
}
