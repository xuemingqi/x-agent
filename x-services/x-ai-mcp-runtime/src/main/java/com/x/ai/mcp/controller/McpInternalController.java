package com.x.ai.mcp.controller;

import com.x.ai.mcp.api.McpRuntimeApi;
import com.x.ai.mcp.api.domain.McpConnectionTestDo;
import com.x.ai.mcp.api.domain.McpRuntimeServerDo;
import com.x.ai.mcp.api.domain.McpServerDo;
import com.x.ai.mcp.api.dto.McpUserCredentialUpdateDto;
import com.x.ai.mcp.api.dto.McpServerCreateDto;
import com.x.ai.mcp.api.dto.McpServerUpdateDto;
import com.x.ai.mcp.api.enums.McpScope;
import com.x.ai.mcp.runtime.McpRuntimeRegistry;
import com.x.ai.mcp.service.McpServerManagementService;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.web.annotation.Internal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Internal
@Tag(name = "McpInternalController", description = "MCP 内部服务管理")
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class McpInternalController implements McpRuntimeApi {

    private final McpServerManagementService managementService;

    private final McpRuntimeRegistry runtimeRegistry;

    @Override
    @Operation(summary = "查询当前用户可见的 MCP 服务")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @GetMapping("/mcp-configurations")
    public List<McpServerDo> list(@RequestHeader(CommonConstant.USER_ID) String userId, Integer scope) {
        return managementService.list(userId, scope == null ? null : McpScope.fromCode(scope));
    }

    @Override
    @Operation(summary = "查询 MCP 服务详情")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @GetMapping("/mcp-configurations/{scope}/{id}")
    public McpServerDo get(@RequestHeader(CommonConstant.USER_ID) String userId,
                           @PathVariable int scope, @PathVariable Long id) {
        return managementService.get(userId, McpScope.fromCode(scope), id);
    }

    @Override
    @Operation(summary = "创建当前用户 MCP 服务")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping("/mcp-configurations")
    public McpServerDo create(@RequestHeader(CommonConstant.USER_ID) String userId, McpServerCreateDto request) {
        return managementService.create(userId, request);
    }

    @Override
    @Operation(summary = "修改当前用户 MCP 服务")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PutMapping("/mcp-configurations/{id}")
    public McpServerDo update(@RequestHeader(CommonConstant.USER_ID) String userId,
                              @PathVariable Long id, McpServerUpdateDto request) {
        return managementService.update(userId, id, request);
    }

    @Override
    @Operation(summary = "配置当前用户的公司内部公共 MCP 凭证")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PutMapping("/mcp-configurations/internal-public/{id}/credential")
    public McpServerDo updateUserCredential(@RequestHeader(CommonConstant.USER_ID) String userId,
                                            @PathVariable Long id, McpUserCredentialUpdateDto request) {
        return managementService.updateUserCredential(userId, id, request);
    }

    @Override
    @Operation(summary = "删除当前用户 MCP 服务")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @DeleteMapping("/mcp-configurations/{id}")
    public boolean delete(@RequestHeader(CommonConstant.USER_ID) String userId, @PathVariable Long id) {
        return managementService.delete(userId, id);
    }

    @Override
    @Operation(summary = "检测 MCP 服务连接并发现工具")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping("/mcp-configurations/{scope}/{id}/connection-test")
    public McpConnectionTestDo testConnection(@RequestHeader(CommonConstant.USER_ID) String userId,
                                              @PathVariable int scope, @PathVariable Long id) {
        return managementService.testConnection(userId, McpScope.fromCode(scope), id);
    }

    @Override
    @Operation(summary = "查询当前用户启用的 MCP 运行配置")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @GetMapping("/mcp-configurations/runtime")
    public List<McpRuntimeServerDo> listRuntimeServers(@RequestHeader(CommonConstant.USER_ID) String userId) {
        return runtimeRegistry.list(userId);
    }
}
