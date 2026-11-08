package com.x.ai.controller;

import com.x.ai.common.dto.ResourceEnabledUpdateDto;
import com.x.ai.mcp.api.domain.McpConnectionTestDo;
import com.x.ai.mcp.api.domain.McpServerDo;
import com.x.ai.mcp.api.dto.McpServerCreateDto;
import com.x.ai.mcp.api.dto.McpServerUpdateDto;
import com.x.ai.mcp.api.dto.McpUserCredentialUpdateDto;
import com.x.ai.mcp.api.enums.McpScope;
import com.x.ai.service.McpServerService;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "McpServerController", description = "MCP 服务管理")

@RequestMapping("/mcps")
@RestController
@RequiredArgsConstructor
public class McpServerController {

    private final McpServerService mcpServerService;

    @Operation(summary = "修改当前用户的扩展启用状态")
    @PutMapping("/{scope}/{id}/enabled")
    public BaseResult<Boolean> setEnabled(@PathVariable int scope, @PathVariable Long id,
                                          @RequestBody @Valid ResourceEnabledUpdateDto request) {
        return ResultUtil.buildResultSuccess(mcpServerService.setEnabled(McpScope.fromCode(scope), id, request.getEnabled()));
    }


    @Operation(summary = "查询当前用户可见的 MCP 服务")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @GetMapping
    public BaseResult<List<McpServerDo>> list(@RequestParam(required = false) Integer scope) {
        return ResultUtil.buildResultSuccess(mcpServerService.list(scope == null ? null : McpScope.fromCode(scope)));
    }

    @Operation(summary = "查询 MCP 服务详情")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @GetMapping("/{scope}/{id}")
    public BaseResult<McpServerDo> get(@PathVariable int scope, @PathVariable Long id) {
        return ResultUtil.buildResultSuccess(mcpServerService.get(McpScope.fromCode(scope), id));
    }

    @Operation(summary = "创建当前用户 MCP 服务")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping
    public BaseResult<McpServerDo> create(@RequestBody @Valid McpServerCreateDto request) {
        return ResultUtil.buildResultSuccess(mcpServerService.create(request));
    }

    @Operation(summary = "修改当前用户 MCP 服务")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PutMapping("/{id}")
    public BaseResult<McpServerDo> update(@PathVariable Long id, @RequestBody @Valid McpServerUpdateDto request) {
        return ResultUtil.buildResultSuccess(mcpServerService.update(id, request));
    }

    @Operation(summary = "配置当前用户的公司内部公共 MCP 凭证")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PutMapping("/internal-public/{id}/credential")
    public BaseResult<McpServerDo> updateUserCredential(@PathVariable Long id,
                                                        @RequestBody @Valid McpUserCredentialUpdateDto request) {
        return ResultUtil.buildResultSuccess(mcpServerService.updateUserCredential(id, request));
    }

    @Operation(summary = "删除当前用户 MCP 服务")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @DeleteMapping("/{id}")
    public BaseResult<Boolean> delete(@PathVariable Long id) {
        return ResultUtil.buildResultSuccess(mcpServerService.delete(id));
    }

    @Operation(summary = "检测 MCP 服务连接并发现工具")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping("/{scope}/{id}/connection-test")
    public BaseResult<McpConnectionTestDo> testConnection(@PathVariable int scope, @PathVariable Long id) {
        return ResultUtil.buildResultSuccess(mcpServerService.testConnection(McpScope.fromCode(scope), id));
    }
}
