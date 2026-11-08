package com.x.ai.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "UserSkillCreateDto", description = "创建用户 Skill")
public class UserSkillCreateDto {

    @NotBlank(message = "Skill 名称不能为空")
    @Size(max = 64, message = "Skill 名称不能超过 64 个字符")
    @Pattern(regexp = "[a-z0-9][a-z0-9._-]*", message = "Skill 名称只能包含小写字母、数字、点、下划线和短横线")
    @Schema(name = "name", description = "Skill 名称")
    private String name;

    @NotBlank(message = "Skill 描述不能为空")
    @Size(max = 500, message = "Skill 描述不能超过 500 个字符")
    @Schema(name = "description", description = "Skill 使用场景描述")
    private String description;

    @NotBlank(message = "Skill 内容不能为空")
    @Size(max = 100_000, message = "Skill 内容不能超过 100000 个字符")
    @Schema(name = "content", description = "Skill 指令正文，不包含 YAML frontmatter")
    private String content;
}
