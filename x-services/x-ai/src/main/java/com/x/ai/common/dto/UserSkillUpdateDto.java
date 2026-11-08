package com.x.ai.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "UserSkillUpdateDto", description = "修改用户 Skill")
public class UserSkillUpdateDto {

    @NotBlank(message = "Skill 描述不能为空")
    @Size(max = 500, message = "Skill 描述不能超过 500 个字符")
    @Schema(name = "description", description = "Skill 使用场景描述")
    private String description;

    @NotBlank(message = "Skill 内容不能为空")
    @Size(max = 100_000, message = "Skill 内容不能超过 100000 个字符")
    @Schema(name = "content", description = "Skill 指令正文，不包含 YAML frontmatter")
    private String content;
}
