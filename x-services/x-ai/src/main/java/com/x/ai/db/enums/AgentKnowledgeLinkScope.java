package com.x.ai.db.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AgentKnowledgeLinkScope {

    PUBLIC(1, "公共"),

    USER(2, "个人");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;
}
