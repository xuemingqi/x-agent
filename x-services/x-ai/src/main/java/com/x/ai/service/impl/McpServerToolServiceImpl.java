package com.x.ai.service.impl;

import com.x.ai.common.domain.McpConnectionResultDo;
import com.x.ai.common.dto.McpConnectionTestToolDto;
import com.x.ai.common.dto.McpServerToolCreateDto;
import com.x.ai.common.dto.McpServerToolDto;
import com.x.ai.common.dto.McpServerToolUpdateDto;
import com.x.ai.common.model.ToolContext;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.mcp.api.McpRuntimeApi;
import com.x.ai.mcp.api.domain.McpServerDo;
import com.x.ai.mcp.api.dto.McpServerCreateDto;
import com.x.ai.mcp.api.dto.McpServerUpdateDto;
import com.x.ai.mcp.api.enums.McpScope;
import com.x.ai.mcp.api.enums.McpTransportType;
import com.x.ai.service.HarnessAgentService;
import com.x.ai.service.McpConnectionService;
import com.x.ai.service.McpServerToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class McpServerToolServiceImpl implements McpServerToolService {

    private final McpRuntimeApi mcpRuntimeApi;

    private final HarnessAgentService harnessAgentService;

    private final McpConnectionService mcpConnectionService;


    @Override
    public List<McpServerToolDto> list(ToolContext toolContext, Integer scope) {
        requireIdentity(toolContext);
        if (scope != null) {
            requireScope(scope);
        }
        return mcpRuntimeApi.list(toolContext.getUserId(), scope).stream().map(this::toToolDto).toList();
    }

    @Override
    public McpServerToolDto get(ToolContext toolContext, int scope, Long id) {
        requireIdentity(toolContext);
        requireScope(scope);
        return toToolDto(mcpRuntimeApi.get(toolContext.getUserId(), scope, id));
    }

    @Override
    public McpConnectionTestToolDto testConnection(ToolContext toolContext, int scope, Long id) {
        requireIdentity(toolContext);
        McpScope resolvedScope = requireScope(scope);
        McpConnectionResultDo result = mcpConnectionService.testConnection(toolContext.getUserId(), resolvedScope, id);
        return new McpConnectionTestToolDto().setId(String.valueOf(result.getId())).setScope(result.getScope().getCode())
                .setConnected(result.isConnected()).setToolNames(result.getToolNames());
    }

    @Override
    public McpServerToolDto create(ToolContext toolContext, McpServerToolCreateDto request) {
        requireIdentity(toolContext);
        if (request == null) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        McpServerCreateDto managementRequest = new McpServerCreateDto()
                .setName(request.getName())
                .setDescription(request.getDescription())
                .setTransportType(toTransportType(request.getTransportType()))
                .setEndpoint(request.getEndpoint())
                .setCommand(request.getCommand())
                .setArguments(request.getArguments())
                .setHeaders(request.getHeaders())
                .setQueryParameters(request.getQueryParameters())
                .setEnvironment(request.getEnvironment())
                .setEnabled(request.isEnabled())
                .setRequestTimeoutSeconds(request.getRequestTimeoutSeconds())
                .setInitializationTimeoutSeconds(request.getInitializationTimeoutSeconds());
        McpServerDo server = mcpRuntimeApi.create(toolContext.getUserId(), managementRequest);
        harnessAgentService.evictUserAgents(toolContext.getUserId());
        return toToolDto(server);
    }

    @Override
    public McpServerToolDto update(ToolContext toolContext, Long id, McpServerToolUpdateDto request) {
        requireIdentity(toolContext);
        if (request == null) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        McpServerUpdateDto managementRequest = new McpServerUpdateDto()
                .setDescription(request.getDescription())
                .setTransportType(toTransportType(request.getTransportType()))
                .setEndpoint(request.getEndpoint())
                .setCommand(request.getCommand())
                .setArguments(request.getArguments())
                .setHeaders(request.getHeaders())
                .setQueryParameters(request.getQueryParameters())
                .setEnvironment(request.getEnvironment())
                .setEnabled(request.isEnabled())
                .setRequestTimeoutSeconds(request.getRequestTimeoutSeconds())
                .setInitializationTimeoutSeconds(request.getInitializationTimeoutSeconds());
        McpServerDo server = mcpRuntimeApi.update(toolContext.getUserId(), id, managementRequest);
        harnessAgentService.evictUserAgents(toolContext.getUserId());
        return toToolDto(server);
    }

    @Override
    public boolean delete(ToolContext toolContext, Long id) {
        requireIdentity(toolContext);
        boolean deleted = mcpRuntimeApi.delete(toolContext.getUserId(), id);
        if (deleted) {
            harnessAgentService.evictUserAgents(toolContext.getUserId());
        }
        return deleted;
    }

    /**
     * 工具出参不依赖不同 Jackson 版本的 Long 注解，凭证值在本边界再次脱敏。
     */
    private McpServerToolDto toToolDto(McpServerDo server) {
        return new McpServerToolDto()
                .setId(String.valueOf(server.getId()))
                .setName(server.getName())
                .setDescription(server.getDescription())
                .setScope(server.getScope().getCode())
                .setTransportType(server.getTransportType() == null ? null : server.getTransportType().getCode())
                .setEndpoint(server.getEndpoint())
                .setCommand(server.getCommand())
                .setArguments(server.getArguments())
                .setHeaders(maskCredentialValues(server.getHeaders()))
                .setQueryParameters(maskCredentialValues(server.getQueryParameters()))
                .setEnvironment(maskCredentialValues(server.getEnvironment()))
                .setEnabled(server.isEnabled())
                .setEditable(server.isEditable())
                .setRuntimeSupported(server.isRuntimeSupported())
                .setRequestTimeoutSeconds(server.getRequestTimeoutSeconds())
                .setInitializationTimeoutSeconds(server.getInitializationTimeoutSeconds());
    }

    /**
     * 保留配置 key 以便编辑，禁止凭证 value 进入模型上下文。
     */
    private Map<String, String> maskCredentialValues(Map<String, String> values) {
        return values == null ? null : values.keySet().stream().collect(Collectors.toMap(key -> key, key -> ""));
    }

    /**
     * 后台任务与对话共享可信身份，不从模型参数或线程登录态推断操作人。
     */
    private void requireIdentity(ToolContext toolContext) {
        if (toolContext == null || StringUtil.isBlank(toolContext.getUserId())) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
    }

    /**
     * 工具只接受已定义的整数作用域，缺少作用域由工具 schema 拒绝。
     */
    private McpScope requireScope(int scope) {
        McpScope resolvedScope = McpScope.fromCode(scope);
        if (resolvedScope == null) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        return resolvedScope;
    }

    /**
     * 将 Tool 使用的整数编码转换为内部枚举。
     */
    private McpTransportType toTransportType(int code) {
        for (McpTransportType transportType : McpTransportType.values()) {
            if (transportType.getCode() == code) {
                return transportType;
            }
        }
        throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
    }
}
