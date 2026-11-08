package com.x.ai.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 截图请求结束状态，客户端据此更新授权卡片。
 */
@Getter
@AllArgsConstructor
public enum BrowserScreenshotStatus {

    RECEIVED("received", "截图已接收"),

    DECLINED("declined", "用户拒绝或取消"),

    EXPIRED("expired", "请求超时"),

    CANCELLED("cancelled", "任务结束或中断");

    @JsonValue
    private final String value;

    private final String desc;
}
