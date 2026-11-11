package com.x.ai.service.impl;

import com.x.ai.common.domain.McpConnectionResultDo;
import com.x.ai.common.domain.McpServerDo;
import com.x.ai.common.dto.McpConnectionTestToolDto;
import com.x.ai.common.dto.McpServerCreateDto;
import com.x.ai.common.dto.McpServerToolCreateDto;
import com.x.ai.common.dto.McpServerToolDto;
import com.x.ai.common.dto.McpServerToolUpdateDto;
import com.x.ai.common.dto.McpServerUpdateDto;
import com.x.ai.common.enums.McpTransportType;
import com.x.ai.common.model.ToolContext;
import com.x.ai.service.HarnessAgentService;
import com.x.ai.service.McpConnectionService;
import com.x.ai.service.McpServerManagementService;
import com.x.ai.service.McpServerToolService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class McpServerToolServiceImpl implements McpServerToolService {

    private final McpServerManagementService mcpServerManagementService;

    private final HarnessAgentService harnessAgentService;

    private final McpConnectionService mcpConnectionService;


    @Override
    public List<McpServerToolDto> list(ToolContext toolContext) {
        requireIdentity(toolContext);
        return mcpServerManagementService.list(toolContext.getUserId())
                .stream().map(this::toToolDto).toList();
    }

    @Override
    public McpServerToolDto get(ToolContext toolContext, Long id) {
        requireIdentity(toolContext);
        return toToolDto(mcpServerManagementService.get(toolContext.getUserId(), id));
    }

    @Override
    public McpConnectionTestToolDto testConnection(ToolContext toolContext, Long id) {
        requireIdentity(toolContext);
        McpConnectionResultDo result = mcpConnectionService.testConnection(toolContext.getUserId(), id);
        return new McpConnectionTestToolDto().setId(String.valueOf(result.getId()))
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
                .setHeaders(request.getHeaders())
                .setQueryParameters(request.getQueryParameters())
                .setEnabled(request.isEnabled())
                .setRequestTimeoutSeconds(request.getRequestTimeoutSeconds())
                .setInitializationTimeoutSeconds(request.getInitializationTimeoutSeconds());
        McpServerDo server = mcpServerManagementService.create(toolContext.getUserId(), managementRequest);
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
                .setHeaders(request.getHeaders())
                .setQueryParameters(request.getQueryParameters())
                .setEnabled(request.isEnabled())
                .setRequestTimeoutSeconds(request.getRequestTimeoutSeconds())
                .setInitializationTimeoutSeconds(request.getInitializationTimeoutSeconds());
        McpServerDo server = mcpServerManagementService.update(toolContext.getUserId(), id, managementRequest);
        harnessAgentService.evictUserAgents(toolContext.getUserId());
        return toToolDto(server);
    }

    @Override
    public boolean delete(ToolContext toolContext, Long id) {
        requireIdentity(toolContext);
        boolean deleted = mcpServerManagementService.delete(toolContext.getUserId(), id);
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
                .setTransportType(server.getTransportType() == null ? null : server.getTransportType().getCode())
                .setEndpoint(server.getEndpoint())
                .setHeaders(maskCredentialValues(server.getHeaders()))
                .setQueryParameters(maskCredentialValues(server.getQueryParameters()))
                .setEnabled(server.isEnabled())
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
