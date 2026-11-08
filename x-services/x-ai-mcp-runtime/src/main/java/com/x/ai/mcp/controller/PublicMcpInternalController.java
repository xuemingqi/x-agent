package com.x.ai.mcp.controller;

import com.x.framework.web.annotation.Internal;
import com.x.ai.mcp.api.PublicMcpRuntimeApi;
import com.x.ai.mcp.api.domain.McpServerDo;
import com.x.ai.mcp.api.dto.PublicMcpServerCreateDto;
import com.x.ai.mcp.api.dto.PublicMcpServerUpdateDto;
import com.x.ai.mcp.service.McpServerManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "PublicMcpInternalController", description = "公共 MCP 内部管理")
@Internal
@Validated
@RestController
@RequestMapping("/internal/public-mcp-configurations")
@RequiredArgsConstructor
public class PublicMcpInternalController implements PublicMcpRuntimeApi {

    private final McpServerManagementService managementService;


    @Override
    @Operation(summary = "查询公共 MCP 管理配置")
    @GetMapping
    public List<McpServerDo> list() {
        return managementService.listPublic();
    }

    @Override
    @Operation(summary = "查询公共 MCP 管理详情")
    @GetMapping("/{id}")
    public McpServerDo get(@PathVariable Long id) {
        return managementService.getPublic(id);
    }

    @Override
    @Operation(summary = "创建公共 MCP 服务")
    @PostMapping
    public McpServerDo create(PublicMcpServerCreateDto request) {
        return managementService.createPublic(request);
    }

    @Override
    @Operation(summary = "修改公共 MCP 服务")
    @PutMapping("/{id}")
    public McpServerDo update(@PathVariable Long id, PublicMcpServerUpdateDto request) {
        return managementService.updatePublic(id, request);
    }

    @Override
    @Operation(summary = "删除公共 MCP 服务")
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        return managementService.deletePublic(id);
    }
}
