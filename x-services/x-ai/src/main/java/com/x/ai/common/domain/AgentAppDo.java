package com.x.ai.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(description = "智能应用目录项")
public class AgentAppDo {

    /**
     * 当前用户个人应用的 UUID 标识。
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

    /**
     * 当前发布版本的 UUID 标识。
     */
    private String currentVersion;
}
