package com.x.ai.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@Schema(name = "McpConnectionTestDo", description = "MCP 连接检测结果")
public class McpConnectionTestDo {

    /**
     * 是否成功连接并完成工具发现。
     */
    @Schema(name = "connected", description = "是否成功连接并完成工具发现")
    private boolean connected;

    /**
     * MCP 服务提供的工具名称。
     */
    @Schema(name = "toolNames", description = "MCP 服务提供的工具名称")
    private List<String> toolNames;
}
