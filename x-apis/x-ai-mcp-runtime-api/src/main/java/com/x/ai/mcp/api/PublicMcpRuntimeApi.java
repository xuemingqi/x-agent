package com.x.ai.mcp.api;

import com.x.ai.mcp.api.config.McpRuntimeApiFeignConfiguration;

import com.x.framework.common.config.InternalInterceptor;
import com.x.ai.mcp.api.domain.McpServerDo;
import com.x.ai.mcp.api.dto.PublicMcpServerCreateDto;
import com.x.ai.mcp.api.dto.PublicMcpServerUpdateDto;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = McpRuntimeApi.SERVICE_NAME,
        contextId = "x-ai-public-mcp-runtime-api",
        configuration = {InternalInterceptor.class, McpRuntimeApiFeignConfiguration.class})
public interface PublicMcpRuntimeApi {

    /**
     * 查询公共 MCP 管理配置。
     *
     * @return 公共 MCP 配置
     */
    @GetMapping("/mcp/internal/public-mcp-configurations")
    List<McpServerDo> list();

    /**
     * 查询公共 MCP 管理详情。
     *
     * @param id MCP 配置 ID
     * @return MCP 配置详情
     */
    @GetMapping("/mcp/internal/public-mcp-configurations/{id}")
    McpServerDo get(@PathVariable("id") Long id);

    /**
     * 创建公共 MCP 配置。
     *
     * @param request MCP 配置
     * @return 已创建的 MCP 配置
     */
    @PostMapping("/mcp/internal/public-mcp-configurations")
    McpServerDo create(@RequestBody @Valid PublicMcpServerCreateDto request);

    /**
     * 修改公共 MCP 配置。
     *
     * @param id      MCP 配置 ID
     * @param request MCP 配置
     * @return 修改后的 MCP 配置
     */
    @PutMapping("/mcp/internal/public-mcp-configurations/{id}")
    McpServerDo update(@PathVariable("id") Long id, @RequestBody @Valid PublicMcpServerUpdateDto request);

    /**
     * 删除公共 MCP 配置。
     *
     * @param id MCP 配置 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/mcp/internal/public-mcp-configurations/{id}")
    boolean delete(@PathVariable("id") Long id);
}
