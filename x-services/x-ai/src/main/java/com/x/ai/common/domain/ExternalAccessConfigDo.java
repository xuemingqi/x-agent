package com.x.ai.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "ExternalAccessConfigDo", description = "外部开发者接入配置")
public class ExternalAccessConfigDo {

    @Schema(name = "httpApiUrl", description = "对外 HTTP Agent 接口地址")
    private String httpApiUrl;

    @Schema(name = "mcpServerUrl", description = "对外 MCP Gateway 地址")
    private String mcpServerUrl;
}
