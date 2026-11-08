package com.x.ai.common.model;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class FileToolContext {

    /**
     * 本轮用户消息 ID。
     */
    private Long messageId;
}
