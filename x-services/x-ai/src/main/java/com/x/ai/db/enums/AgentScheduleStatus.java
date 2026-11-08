package com.x.ai.db.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AgentScheduleStatus {

    ACTIVE(1, "启用"),

    PAUSED(2, "已暂停");


    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;

    /**
     * 根据编码获取任务状态。
     *
     * @param code 任务状态编码
     * @return 任务状态，不存在时返回 null
     */
    public static AgentScheduleStatus fromCode(int code) {
        for (AgentScheduleStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
