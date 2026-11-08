package com.x.ai.db.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AgentSkillShareStatus {

    PENDING(1, "待处理"),

    ACCEPTED(2, "已接受"),

    REJECTED(3, "已拒绝");


    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;
}
