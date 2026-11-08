package com.x.ai.mcp.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum McpCredentialPlacement {

    HTTP_HEADER(1, "HTTP Header"),

    QUERY_PARAMETER(2, "HTTP Query Parameter"),

    ENVIRONMENT_VARIABLE(3, "STDIO Environment Variable");


    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;
}
