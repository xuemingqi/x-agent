package com.x.ai.db.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AgentScheduleTriggerSource {

    SCHEDULED(1, "定时触发"),

    MANUAL(2, "手动触发");


    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;
}
