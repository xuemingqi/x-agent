package com.x.ai.common.enums;

import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum SkillManagementAction {

    CREATE("create", "创建 Skill"),

    EDIT("edit", "修改 Skill 正文"),

    PATCH("patch", "精确替换正文或资源文本"),

    WRITE_FILE("write_file", "写入资源文件"),

    REMOVE_FILE("remove_file", "删除资源文件"),

    DELETE("delete", "删除 Skill");


    @JsonValue
    private final String code;

    private final String desc;

    /**
     * 根据工具操作编码获取管理动作，未知编码按 Skill 参数错误处理。
     *
     * @param code 操作编码
     * @return Skill 管理动作
     */
    public static SkillManagementAction fromCode(String code) {
        return Arrays.stream(values())
                .filter(action -> action.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new XException(ResponseCodeEnum.AGENT_SKILL_INVALID));
    }
}
