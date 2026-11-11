package com.x.ai.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum McpTransportType {

    STREAMABLE_HTTP(1, "Streamable HTTP", true);


    /**
     * 数据库与接口共用的稳定传输编码。
     */
    @EnumValue
    @JsonValue
    private final int code;

    /**
     * 传输协议说明。
     */
    private final String desc;

    /**
     * 是否可供 Agent 直接连接。
     */
    private final boolean runtimeSupported;
}
