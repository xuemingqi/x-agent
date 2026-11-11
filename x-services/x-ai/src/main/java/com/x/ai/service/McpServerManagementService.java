package com.x.ai.service;

import com.x.ai.common.domain.McpConnectionTestDo;
import com.x.ai.common.domain.McpServerDo;
import com.x.ai.common.dto.McpServerCreateDto;
import com.x.ai.common.dto.McpServerUpdateDto;
import com.x.ai.common.model.McpServer;

import java.util.List;

public interface McpServerManagementService {

    /**
     * 查询当前用户已启用的 HTTP MCP，用于直接创建连接。
     */
    List<McpServer> listRuntimeServers(String userId);

    /**
     * 查询指定用户拥有的 MCP 服务。
     *
     * @param userId 用户 ID
     * @return MCP 服务列表
     */
    List<McpServerDo> list(String userId);

    /**
     * 查询指定用户拥有的 MCP 服务详情。
     *
     * @param userId 用户 ID
     * @param id     MCP 服务配置 ID
     * @return MCP 服务详情
     */
    McpServerDo get(String userId, Long id);

    /**
     * 修改指定用户 MCP 的启用状态。
     */
    boolean setEnabled(String userId, Long id, boolean enabled);

    /**
     * 为指定用户创建个人 MCP 服务。
     *
     * @param userId  用户 ID
     * @param request MCP 配置
     * @return 已创建 MCP 服务
     */
    McpServerDo create(String userId, McpServerCreateDto request);

    /**
     * 修改指定用户的个人 MCP 服务。
     *
     * @param userId  用户 ID
     * @param id      MCP 服务配置 ID
     * @param request MCP 配置
     * @return 修改后的 MCP 服务
     */
    McpServerDo update(String userId, Long id, McpServerUpdateDto request);

    /**
     * 删除指定用户的个人 MCP 服务。
     *
     * @param userId 用户 ID
     * @param id     MCP 服务配置 ID
     * @return 是否删除成功
     */
    boolean delete(String userId, Long id);

    /**
     * 检测指定用户拥有的 MCP 服务并返回发现的工具。
     *
     * @param userId 用户 ID
     * @param id     MCP 服务配置 ID
     * @return 连接检测结果
     */
    McpConnectionTestDo testConnection(String userId, Long id);
}
