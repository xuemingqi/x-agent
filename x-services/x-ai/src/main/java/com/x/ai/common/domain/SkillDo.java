package com.x.ai.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@Schema(name = "SkillDo", description = "Agent Skill")
public class SkillDo {

    @Schema(name = "name", description = "Skill 名称")
    private String name;

    @Schema(name = "description", description = "Skill 使用场景描述")
    private String description;

    @Schema(name = "content", description = "Skill 指令正文")
    private String content;

    /**
     * 当前用户的独立启用偏好，未设置时默认启用。
     */
    @Schema(description = "当前用户是否启用")
    private boolean enabled = true;

    @Schema(name = "resourcePaths", description = "Skill 附属资源相对路径")
    private List<String> resourcePaths;
}
