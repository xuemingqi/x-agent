package com.x.ai.db.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum AgentModelMultimodalCapability {

    NONE(0, "纯文本", false, false),

    IMAGE_INPUT(1, "图片输入", true, false),

    IMAGE_OUTPUT(2, "图片输出", false, true),

    IMAGE_INPUT_OUTPUT(3, "图片输入及输出", true, true);

    /**
     * 模型表及接口共用的能力编码，默认 0 表示纯文本。
     */
    @EnumValue
    @JsonValue
    private final int code;

    /**
     * 能力描述。
     */
    private final String desc;

    /**
     * 是否将用户图片直接传给模型。
     */
    private final boolean imageInput;

    /**
     * 是否请求模型同时返回文字和图片。
     */
    private final boolean imageOutput;
    /**
     * 解码接口能力值，非法编码不降级为纯文本。
     */
    public static AgentModelMultimodalCapability fromCode(Integer code) {
        return Arrays.stream(values()).filter(value -> value.getCode() == code).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("无效的模型多模态能力编码"));
    }
}
