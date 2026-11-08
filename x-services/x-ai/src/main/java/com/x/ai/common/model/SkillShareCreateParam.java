package com.x.ai.common.model;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 分享 Skill 的业务参数，由 Web 与 Tool 入口分别转换，操作人由可信身份单独传入。
 */
@Data
@Accessors(chain = true)
public class SkillShareCreateParam {

    @NotBlank(message = "接收人手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "接收人手机号格式无效")
    @Size(max = 11, message = "接收人手机号不能超过 11 个字符")
    private String recipientMobile;
}
