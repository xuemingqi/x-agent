package com.x.ai.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "SkillShareCreateDto", description = "创建 Skill 分享邀请")
public class SkillShareCreateDto {

    @NotBlank(message = "接收人手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "接收人手机号格式无效")
    @Size(max = 11, message = "接收人手机号不能超过 11 个字符")
    @Schema(name = "recipientMobile", description = "分享接收人的平台手机号")
    private String recipientMobile;
}
