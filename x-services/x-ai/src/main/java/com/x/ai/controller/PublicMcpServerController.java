package com.x.ai.controller;

import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import com.x.ai.mcp.api.domain.McpServerDo;
import com.x.ai.mcp.api.dto.PublicMcpServerCreateDto;
import com.x.ai.mcp.api.dto.PublicMcpServerUpdateDto;
import com.x.ai.service.PublicMcpServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "PublicMcpServerController", description = "管理员公共 MCP 管理")
@RequestMapping("/administration/mcps")
@RestController
@RequiredArgsConstructor
public class PublicMcpServerController {

    private final PublicMcpServerService publicMcpServerService;

    @Operation(summary = "查询公共 MCP 管理配置")
    @GetMapping
    public BaseResult<List<McpServerDo>> list() {
        return ResultUtil.buildResultSuccess(publicMcpServerService.list(currentUserId()));
    }

    @Operation(summary = "查询公共 MCP 管理详情")
    @GetMapping("/{id}")
    public BaseResult<McpServerDo> get(@PathVariable Long id) {
        return ResultUtil.buildResultSuccess(publicMcpServerService.get(currentUserId(), id));
    }

    @Operation(summary = "创建公共 MCP 服务")
    @PostMapping
    public BaseResult<McpServerDo> create(@RequestBody @Valid PublicMcpServerCreateDto request) {
        return ResultUtil.buildResultSuccess(publicMcpServerService.create(currentUserId(), request));
    }

    @Operation(summary = "修改公共 MCP 服务")
    @PutMapping("/{id}")
    public BaseResult<McpServerDo> update(@PathVariable Long id, @RequestBody @Valid PublicMcpServerUpdateDto request) {
        return ResultUtil.buildResultSuccess(publicMcpServerService.update(currentUserId(), id, request));
    }

    @Operation(summary = "删除公共 MCP 服务")
    @DeleteMapping("/{id}")
    public BaseResult<Boolean> delete(@PathVariable Long id) {
        return ResultUtil.buildResultSuccess(publicMcpServerService.delete(currentUserId(), id));
    }

    /**
     * 从认证边界读取当前可信用户 ID。
     */
    private String currentUserId() {
        return UserThreadLocalUtil.getUserId();
    }
}
