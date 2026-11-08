package com.x.ai.service;

import com.x.ai.mcp.api.domain.McpConnectionTestDo;
import com.x.ai.mcp.api.domain.McpServerDo;
import com.x.ai.mcp.api.dto.McpServerCreateDto;
import com.x.ai.mcp.api.dto.McpServerUpdateDto;
import com.x.ai.mcp.api.dto.McpUserCredentialUpdateDto;
import com.x.ai.mcp.api.enums.McpScope;

import java.util.List;

public interface McpServerService {

    /**
     * 保存当前用户对可见 MCP 的启用偏好，不改变公共配置或其他用户的使用状态。
     */
    boolean setEnabled(McpScope scope, Long id, boolean enabled);

    /**
     * 查询当前用户可见的公共和个人 MCP 服务。
     *
     * @param scope 可选作用域，为空时返回全部
     * @return MCP 服务列表
     */
    List<McpServerDo> list(McpScope scope);

    /**
     * 查询当前用户可见的 MCP 服务详情。
     *
     * @param scope MCP 作用域
     * @param id    MCP 服务配置 ID
     * @return MCP 服务详情
     */
    McpServerDo get(McpScope scope, Long id);

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
     * 配置当前用户的公司内部公共 MCP 凭证。
     *
     * @param id      公共 MCP 服务配置 ID
     * @param request 鉴权配置
     * @return 配置后的公共 MCP 服务
     */
    McpServerDo updateUserCredential(Long id, McpUserCredentialUpdateDto request);

    /**
     * 删除当前用户的 MCP 服务。
     *
     * @param id    MCP 服务配置 ID
     * @return 是否删除成功
     */
    boolean delete(Long id);

    /**
     * 检测当前用户可见的 MCP 服务并返回发现的工具。
     *
     * @param scope MCP 作用域
     * @param id MCP 服务配置 ID
     * @return 连接检测结果
     */
    McpConnectionTestDo testConnection(McpScope scope, Long id);
}
