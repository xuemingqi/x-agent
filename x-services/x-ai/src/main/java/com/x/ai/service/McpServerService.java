package com.x.ai.service;

import com.x.ai.common.domain.McpConnectionTestDo;
import com.x.ai.common.domain.McpServerDo;
import com.x.ai.common.dto.McpServerCreateDto;
import com.x.ai.common.dto.McpServerUpdateDto;

import java.util.List;

public interface McpServerService {

    /**
     * 修改当前用户 MCP 的启用状态。
     */
    boolean setEnabled(Long id, boolean enabled);

    /**
     * 查询当前用户拥有的 MCP 服务。
     *
     * @return MCP 服务列表
     */
    List<McpServerDo> list();

    /**
     * 查询当前用户拥有的 MCP 服务详情。
     *
     * @param id    MCP 服务配置 ID
     * @return MCP 服务详情
     */
    McpServerDo get(Long id);

    /**
     * 创建当前用户的 MCP 服务。
     *
     * @param request MCP 配置
     * @return 已创建 MCP 服务
     */
    McpServerDo create(McpServerCreateDto request);

    /**
     * 修改当前用户的 MCP 服务。
     *
     * @param id      MCP 服务配置 ID
     * @param request MCP 配置
     * @return 修改后的 MCP 服务
     */
    McpServerDo update(Long id, McpServerUpdateDto request);

    /**
     * 删除当前用户的 MCP 服务。
     *
     * @param id    MCP 服务配置 ID
     * @return 是否删除成功
     */
    boolean delete(Long id);

    /**
     * 检测当前用户拥有的 MCP 服务并返回发现的工具。
     *
     * @param id MCP 服务配置 ID
     * @return 连接检测结果
     */
    McpConnectionTestDo testConnection(Long id);
}
