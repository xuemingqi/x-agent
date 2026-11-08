package com.x.ai.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(description = "代码注册的智能应用")
public class AgentAppDo {

    /**
     * 稳定的应用标识，同时作为前端页面注册键。
     */
    @Schema(description = "应用标识")
    private String id;

    /**
     * 应用中心展示的名称。
     */
    @Schema(description = "应用名称")
    private String name;

    /**
     * 面向使用者的能力说明。
     */
    @Schema(description = "应用介绍")
    private String description;
}
