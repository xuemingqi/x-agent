package com.x.ai.common.dto;

import com.x.ai.common.model.HttpRequestSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AgentAppHttpRequestDto {

    /**
     * 调用页面加载的发布版本，旧版本不能继承新版本权限。
     */
    @NotBlank
    @Size(max = 64)
    private String version;

    /**
     * 应用显式配置的单次 HTTP 请求，不继承平台身份凭据。
     */
    @Valid
    @NotNull
    private HttpRequestSpec request;
}
