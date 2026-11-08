package com.x.ai.common.model;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UserContext {

    /**
     * 当前用户 ID。
     */
    private String userId;
}
