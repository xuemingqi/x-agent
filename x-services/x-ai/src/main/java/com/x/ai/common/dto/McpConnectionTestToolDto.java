package com.x.ai.common.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 本次 MCP 连接检测及工具发现结果，不包含配置凭证或用户身份引用。
 */
@Data
@Accessors(chain = true)
public class McpConnectionTestToolDto {

    private String id;

    private int scope;

    private boolean connected;

    private List<String> toolNames = List.of();
}
