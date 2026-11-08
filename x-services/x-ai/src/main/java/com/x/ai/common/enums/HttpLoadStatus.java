package com.x.ai.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HttpLoadStatus {

    RUNNING("RUNNING", "运行中"),
    COMPLETED("COMPLETED", "已完成"),
    STOPPED("STOPPED", "已停止"),
    FAILED("FAILED", "执行失败");

    /**
     * 页面使用的稳定状态代码。
     */
    @JsonValue
    private final String code;

    /**
     * 运行状态说明。
     */
    private final String description;
}
