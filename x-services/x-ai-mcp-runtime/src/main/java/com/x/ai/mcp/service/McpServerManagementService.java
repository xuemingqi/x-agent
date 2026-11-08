package com.x.ai.mcp.service;

import com.x.ai.mcp.api.domain.McpConnectionTestDo;
import com.x.ai.mcp.api.domain.McpServerDo;
import com.x.ai.mcp.api.dto.McpUserCredentialUpdateDto;
import com.x.ai.mcp.api.dto.McpServerCreateDto;
import com.x.ai.mcp.api.dto.McpServerUpdateDto;
import com.x.ai.mcp.api.dto.PublicMcpServerCreateDto;
import com.x.ai.mcp.api.dto.PublicMcpServerUpdateDto;
import com.x.ai.mcp.api.enums.McpScope;

import java.util.List;

public interface McpServerManagementService {

    /**
     * 查询管理员可维护的公共 MCP 配置，凭证仅返回字段名。
     */
    List<McpServerDo> listPublic();

    /**
     * 查询公共 MCP 管理详情。
     */
    McpServerDo getPublic(Long id);

    /**
     * 创建公共 MCP 配置，名称不得与已有用户工具命名空间冲突。
     */
    McpServerDo createPublic(PublicMcpServerCreateDto request);

    /**
     * 更新公共 MCP 配置并使全部用户持有的旧连接失效。
     */
    McpServerDo updatePublic(Long id, PublicMcpServerUpdateDto request);

    /**
     * 删除公共 MCP 配置并关闭全部用户持有的旧连接。
     */
    boolean deletePublic(Long id);

    /**
     * 查询指定用户可见的公共和个人 MCP 服务。
     *
     * @param userId 用户 ID
     * @param scope  可选作用域，为空时返回全部
     * @return MCP 服务列表
     */
    List<McpServerDo> list(String userId, McpScope scope);

    /**
     * 查询指定用户可见的 MCP 服务详情。
     *
     * @param userId 用户 ID
     * @param scope  MCP 作用域
     * @param id     MCP 服务配置 ID
     * @return MCP 服务详情
     */
    McpServerDo get(String userId, McpScope scope, Long id);

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
     * 配置指定用户的公司内部公共 MCP 凭证。
     *
     * @param userId  用户 ID
     * @param id      公共 MCP 服务配置 ID
     * @param request 鉴权配置
     * @return 配置后的公共 MCP 服务
     */
    McpServerDo updateUserCredential(String userId, Long id, McpUserCredentialUpdateDto request);

    /**
     * 删除指定用户的个人 MCP 服务。
     *
     * @param userId 用户 ID
     * @param id     MCP 服务配置 ID
     * @return 是否删除成功
     */
    boolean delete(String userId, Long id);

    /**
     * 检测指定用户可见的 MCP 服务并返回发现的工具。
     *
     * @param userId 用户 ID
     * @param scope  MCP 作用域
     * @param id     MCP 服务配置 ID
     * @return 连接检测结果
     */
    McpConnectionTestDo testConnection(String userId, McpScope scope, Long id);
}
