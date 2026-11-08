package com.x.ai.mcp.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum McpTransportType {

    STREAMABLE_HTTP(1, "Streamable HTTP", true),

    STDIO(2, "STDIO", true);


    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;

    private final boolean runtimeSupported;
}
