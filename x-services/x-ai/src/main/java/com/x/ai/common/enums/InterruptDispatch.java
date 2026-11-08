package com.x.ai.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum InterruptDispatch {

    LOCAL(1, "本地中断"),

    DISTRIBUTED(2, "分布式中断");


    @JsonValue
    private final int code;

    private final String desc;
}
