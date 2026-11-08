package com.x.ai.common.domain;

import com.x.framework.common.annotation.LogIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "ApiKeyDo", description = "新申请的 API Key，明文仅返回一次")
public class ApiKeyDo {

    @ToString.Exclude
    @LogIgnore
    @Schema(name = "apiKey", description = "API Key 明文")
    private String apiKey;
}
