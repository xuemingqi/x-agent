package com.x.ai.db.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AgentScheduleExecutionStatus {

    CREATED(1, "已创建"),

    RUNNING(2, "执行中"),

    SUCCESS(3, "执行成功"),

    FAILED(4, "执行失败"),

    INTERRUPTED(5, "已中断");


    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;
}
