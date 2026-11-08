package com.x.ai.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 事件所属的执行层级。
 */
@Getter
@AllArgsConstructor
public enum AgentEventScope {

    MAIN("main"),

    SUBAGENT("subagent");

    @JsonValue
    private final String value;
}
