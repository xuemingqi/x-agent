package com.x.ai.common.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * MCP 连接检测的共享业务结果，入口各自转换为 Web 或工具输出。
 */
@Data
@Accessors(chain = true)
public class McpConnectionResultDo {

    /**
     * 检测的 MCP 配置 ID。
     */
    private Long id;

    /**
     * 是否成功连接并发现工具。
     */
    private boolean connected;

    /**
     * 本次连接发现的工具名称，无工具时为空列表。
     */
    private List<String> toolNames;
}
