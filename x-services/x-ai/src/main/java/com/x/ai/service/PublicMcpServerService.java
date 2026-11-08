package com.x.ai.service;

import com.x.ai.mcp.api.domain.McpServerDo;
import com.x.ai.mcp.api.dto.PublicMcpServerCreateDto;
import com.x.ai.mcp.api.dto.PublicMcpServerUpdateDto;

import java.util.List;

public interface PublicMcpServerService {

    /**
     * 查询公共 MCP 管理配置。
     */
    List<McpServerDo> list(String actorUserId);

    /**
     * 查询公共 MCP 管理详情。
     */
    McpServerDo get(String actorUserId, Long id);

    /**
     * 创建公共 MCP 配置。
     */
    McpServerDo create(String actorUserId, PublicMcpServerCreateDto request);

    /**
     * 修改公共 MCP 配置。
     */
    McpServerDo update(String actorUserId, Long id, PublicMcpServerUpdateDto request);

    /**
     * 删除公共 MCP 配置。
     */
    boolean delete(String actorUserId, Long id);
}
