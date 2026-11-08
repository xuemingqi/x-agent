package com.x.ai.mcp.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum McpScope {

    PUBLIC(1, "公共 MCP"),

    USER(2, "个人 MCP"),

    INTERNAL_PUBLIC(3, "公司内部公共 MCP");


    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;

    /**
     * 根据编码获取 MCP 作用域。
     *
     * @param code 作用域编码
     * @return MCP 作用域，不存在时返回 null
     */
    public static McpScope fromCode(int code) {
        for (McpScope scope : values()) {
            if (scope.code == code) {
                return scope;
            }
        }
        return null;
    }
}
