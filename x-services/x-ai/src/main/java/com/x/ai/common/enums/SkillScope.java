package com.x.ai.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SkillScope {

    PUBLIC(1, "公共 Skill"),

    USER(2, "个人 Skill"),

    SHARED(3, "共享 Skill");


    @JsonValue
    private final int code;

    private final String desc;

    /**
     * 根据编码获取 Skill 作用域。
     *
     * @param code 作用域编码
     * @return Skill 作用域
     */
    public static SkillScope fromCode(int code) {
        for (SkillScope scope : values()) {
            if (scope.code == code) {
                return scope;
            }
        }
        return null;
    }
}
