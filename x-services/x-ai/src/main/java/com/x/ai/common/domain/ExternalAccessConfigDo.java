package com.x.ai.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "ExternalAccessConfigDo", description = "外部开发者接入配置")
public class ExternalAccessConfigDo {

    /**
     * 对外 HTTP Agent 接口的完整地址。
     */
    @Schema(name = "httpApiUrl", description = "对外 HTTP Agent 接口地址")
    private String httpApiUrl;

    /**
     * AI 服务提供的对外 Streamable HTTP MCP 地址。
     */
    @Schema(name = "mcpServerUrl", description = "对外 MCP 服务地址")
    private String mcpServerUrl;
}
