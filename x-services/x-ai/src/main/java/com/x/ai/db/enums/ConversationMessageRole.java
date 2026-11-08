package com.x.ai.db.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ConversationMessageRole {

    USER("USER", "用户"),

    ASSISTANT("ASSISTANT", "Agent"),

    TOOL("TOOL", "工具");

    @EnumValue
    @JsonValue
    private final String value;

    private final String desc;
}
