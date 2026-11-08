package com.x.ai.mcp.api;

import com.x.ai.mcp.api.config.McpRuntimeApiFeignConfiguration;

import com.x.ai.mcp.api.domain.McpConnectionTestDo;
import com.x.ai.mcp.api.domain.McpRuntimeServerDo;
import com.x.ai.mcp.api.domain.McpServerDo;
import com.x.ai.mcp.api.dto.McpUserCredentialUpdateDto;
import com.x.ai.mcp.api.dto.McpServerCreateDto;
import com.x.ai.mcp.api.dto.McpServerUpdateDto;
import com.x.framework.common.config.InternalInterceptor;
import com.x.framework.common.constants.CommonConstant;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = McpRuntimeApi.SERVICE_NAME,
        configuration = {InternalInterceptor.class, McpRuntimeApiFeignConfiguration.class},
        contextId = "x-ai-mcp-runtime-api")
public interface McpRuntimeApi {

    /**
     * Feign 管理接口和 MCP 协议连接共用的服务发现名称。
     */
    String SERVICE_NAME = "x-ai-mcp-runtime";

    /**
     * 查询指定用户可见的 MCP 配置。
     *
     * @param userId 用户 ID
     * @param scope  可选作用域编码，为空时返回全部
     * @return MCP 配置列表
     */
    @GetMapping("/mcp/internal/mcp-configurations")
    List<McpServerDo> list(@RequestHeader(CommonConstant.USER_ID) String userId,
                           @RequestParam(name = "scope", required = false) Integer scope);

    /**
     * 查询指定用户可见的 MCP 配置详情。
     *
     * @param userId 用户 ID
     * @param scope  MCP 作用域编码
     * @param id     MCP 配置 ID
     * @return MCP 配置详情
     */
    @GetMapping("/mcp/internal/mcp-configurations/{scope}/{id}")
    McpServerDo get(@RequestHeader(CommonConstant.USER_ID) String userId,
                    @PathVariable("scope") int scope, @PathVariable("id") Long id);

    /**
     * 创建指定用户的个人 MCP 配置。
     *
     * @param userId  用户 ID
     * @param request MCP 配置
     * @return 已创建的 MCP 配置
     */
    @PostMapping("/mcp/internal/mcp-configurations")
    McpServerDo create(@RequestHeader(CommonConstant.USER_ID) String userId,
                       @RequestBody @Valid McpServerCreateDto request);

    /**
     * 修改指定用户的个人 MCP 配置。
     *
     * @param userId  用户 ID
     * @param id      MCP 配置 ID
     * @param request MCP 配置
     * @return 修改后的 MCP 配置
     */
    @PutMapping("/mcp/internal/mcp-configurations/{id}")
    McpServerDo update(@RequestHeader(CommonConstant.USER_ID) String userId,
                       @PathVariable("id") Long id, @RequestBody @Valid McpServerUpdateDto request);

    /**
     * 配置当前用户的公司内部公共 MCP 凭证。
     *
     * @param userId  用户 ID
     * @param id      公共 MCP 配置 ID
     * @param request 鉴权配置
     * @return 配置后的公共 MCP
     */
    @PutMapping("/mcp/internal/mcp-configurations/internal-public/{id}/credential")
    McpServerDo updateUserCredential(@RequestHeader(CommonConstant.USER_ID) String userId,
                                     @PathVariable("id") Long id,
                                     @RequestBody @Valid McpUserCredentialUpdateDto request);

    /**
     * 删除指定用户的个人 MCP 配置。
     *
     * @param userId 用户 ID
     * @param id     MCP 配置 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/mcp/internal/mcp-configurations/{id}")
    boolean delete(@RequestHeader(CommonConstant.USER_ID) String userId, @PathVariable("id") Long id);

    /**
     * 检测指定用户可见的 MCP 配置连接。
     *
     * @param userId 用户 ID
     * @param scope  MCP 作用域编码
     * @param id     MCP 配置 ID
     * @return 连接检测结果
     */
    @PostMapping("/mcp/internal/mcp-configurations/{scope}/{id}/connection-test")
    McpConnectionTestDo testConnection(@RequestHeader(CommonConstant.USER_ID) String userId,
                                       @PathVariable("scope") int scope, @PathVariable("id") Long id);

    /**
     * 查询指定用户创建 Agent 时需要注册的 MCP 运行描述。
     *
     * @param userId 用户 ID
     * @return MCP 运行描述列表
     */
    @GetMapping("/mcp/internal/mcp-configurations/runtime")
    List<McpRuntimeServerDo> listRuntimeServers(@RequestHeader(CommonConstant.USER_ID) String userId);
}
