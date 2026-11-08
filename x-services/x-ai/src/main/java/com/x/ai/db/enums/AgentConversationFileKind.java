package com.x.ai.db.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AgentConversationFileKind {

    INPUT(1, "用户附件"),

    OUTPUT(2, "Agent 产物");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;
}
