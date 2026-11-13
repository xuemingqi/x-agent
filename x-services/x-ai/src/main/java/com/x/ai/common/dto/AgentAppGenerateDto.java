package com.x.ai.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AgentAppGenerateDto {

    /**
     * 页面加载的发布版本，变化后必须重新加载应用。
     */
    @NotBlank
    @Size(max = 64)
    private String version;

    /**
     * 本次生成输入，不包含平台身份或模型凭证。
     */
    @NotBlank
    @Size(max = 100_000)
    private String prompt;

    /**
     * 应用自定义的生成规则；为空时使用通用生成规则。
     */
    @Size(max = 20_000)
    private String systemPrompt;

    /**
     * 当前用户拥有的模型配置 ID；为空时使用个人默认模型。
     */
    private Long modelId;
}
