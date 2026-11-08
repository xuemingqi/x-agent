package com.x.ai.service;

import com.x.ai.common.dto.McpConnectionTestToolDto;
import com.x.ai.common.dto.McpServerToolCreateDto;
import com.x.ai.common.dto.McpServerToolDto;
import com.x.ai.common.dto.McpServerToolUpdateDto;
import com.x.ai.common.model.ToolContext;

import java.util.List;

public interface McpServerToolService {

    /**
     * 查询指定用户可见的 MCP 服务。
     *
     * @param toolContext Agent 工具可信上下文
     * @param scope       可选作用域编码，为空时返回全部
     * @return MCP 服务列表
     */
    List<McpServerToolDto> list(ToolContext toolContext, Integer scope);

    /**
     * 查询指定用户可见的 MCP 服务详情。
     *
     * @param toolContext Agent 工具可信上下文
     * @param scope       作用域编码
     * @param id          MCP 服务配置 ID
     * @return MCP 服务详情
     */
    McpServerToolDto get(ToolContext toolContext, int scope, Long id);

    /**
     * 检测当前用户可见且机器人已授权的 MCP，返回本次发现的工具名称，不执行工具业务。
     *
     * @param toolContext Agent 工具可信上下文
     * @param scope       必填作用域编码
     * @param id          MCP 服务配置 ID
     * @return 连接状态和发现的工具名称
     */
    McpConnectionTestToolDto testConnection(ToolContext toolContext, int scope, Long id);

    /**
     * 为指定用户创建个人 MCP 服务。
     *
     * @param toolContext Agent 工具可信上下文
     * @param request     MCP 配置
     * @return 已创建 MCP 服务
     */
    McpServerToolDto create(ToolContext toolContext, McpServerToolCreateDto request);

    /**
     * 修改指定用户的个人 MCP 服务。
     *
     * @param toolContext Agent 工具可信上下文
     * @param id          MCP 服务配置 ID
     * @param request     MCP 配置
     * @return 修改后的 MCP 服务
     */
    McpServerToolDto update(ToolContext toolContext, Long id, McpServerToolUpdateDto request);

    /**
     * 删除指定用户的个人 MCP 服务。
     *
     * @param toolContext Agent 工具可信上下文
     * @param id          MCP 服务配置 ID
     * @return 是否删除成功
     */
    boolean delete(ToolContext toolContext, Long id);
}
