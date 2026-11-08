package com.x.ai.db.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AgentScheduleMode {

    CRON(1, "Cron 表达式"),

    FIXED_RATE(2, "固定频率"),

    FIXED_DELAY(3, "固定延迟");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;

    /**
     * 根据编码获取调度模式。
     *
     * @param code 调度模式编码
     * @return 调度模式，不存在时返回 null
     */
    public static AgentScheduleMode fromCode(int code) {
        for (AgentScheduleMode mode : values()) {
            if (mode.code == code) {
                return mode;
            }
        }
        return null;
    }
}
