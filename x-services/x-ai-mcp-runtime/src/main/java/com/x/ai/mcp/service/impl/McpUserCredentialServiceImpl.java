package com.x.ai.mcp.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.mcp.api.enums.McpCredentialPlacement;
import com.x.ai.mcp.api.enums.McpScope;
import com.x.ai.mcp.api.enums.McpTransportType;
import com.x.ai.mcp.converter.McpServerConverter;
import com.x.ai.mcp.db.entity.AgentMcpServer;
import com.x.ai.mcp.db.entity.AgentMcpUserCredential;
import com.x.ai.mcp.db.service.AgentMcpUserCredentialIService;
import com.x.ai.mcp.model.McpServer;
import com.x.ai.mcp.service.McpUserCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class McpUserCredentialServiceImpl implements McpUserCredentialService {

    private final AgentMcpUserCredentialIService credentialIService;

    private final McpServerConverter mcpServerConverter;

    private final Snowflake snowflake;


    @Override
    public Set<Long> configuredServerIds(String userId, Collection<AgentMcpServer> servers) {
        if (CollectionUtils.isEmpty(servers)) {
            return Set.of();
        }
        Map<Long, String> serverGroups = servers.stream()
                .filter(this::requiresUserCredential)
                .filter(server -> !StringUtil.isBlank(server.getCredentialGroup()))
                .collect(Collectors.toUnmodifiableMap(AgentMcpServer::getId,
                        server -> server.getCredentialGroup().trim()));
        if (serverGroups.isEmpty()) {
            return Set.of();
        }
        Set<String> configuredGroups = credentialIService.lambdaQuery()
                .select(AgentMcpUserCredential::getCredentialGroup)
                .eq(AgentMcpUserCredential::getUserId, userId)
                .in(AgentMcpUserCredential::getCredentialGroup, Set.copyOf(serverGroups.values()))
                .list()
                .stream()
                .map(AgentMcpUserCredential::getCredentialGroup)
                .collect(Collectors.toUnmodifiableSet());
        return serverGroups.entrySet().stream()
                .filter(entry -> configuredGroups.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String save(String userId, AgentMcpServer server, String credential) {
        String group = credentialGroup(server);
        String value = normalize(credential);
        if (update(userId, group, value)) {
            return group;
        }
        AgentMcpUserCredential credentialEntity = new AgentMcpUserCredential()
                .setId(snowflake.nextId())
                .setUserId(userId)
                .setCredentialGroup(group)
                .setCredentialValue(value);
        try {
            if (!credentialIService.save(credentialEntity)) {
                throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
            }
        } catch (DuplicateKeyException error) {
            if (!update(userId, group, value)) {
                throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
            }
        }
        return group;
    }

    @Override
    public McpServer toRuntimeServer(String userId, AgentMcpServer server) {
        McpServer runtimeServer = mcpServerConverter.convert(server);
        if (!requiresUserCredential(server)) {
            return runtimeServer;
        }
        AgentMcpUserCredential credential = find(userId, credentialGroup(server));
        if (credential == null) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        return inject(runtimeServer, server, credential.getCredentialValue());
    }

    @Override
    public boolean requiresUserCredential(AgentMcpServer server) {
        return server.getScope() == McpScope.INTERNAL_PUBLIC;
    }

    /**
     * 获取 MCP 的共享凭证组。
     */
    private String credentialGroup(AgentMcpServer server) {
        String group = server.getCredentialGroup();
        if (StringUtil.isBlank(group)) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_CONFIG_ERROR);
        }
        return group.trim();
    }

    /**
     * 按用户和凭证组更新已有凭证。
     */
    private boolean update(String userId, String credentialGroup, String credential) {
        return credentialIService.lambdaUpdate()
                .eq(AgentMcpUserCredential::getUserId, userId)
                .eq(AgentMcpUserCredential::getCredentialGroup, credentialGroup)
                .set(AgentMcpUserCredential::getCredentialValue, credential)
                .update();
    }

    /**
     * 查询指定用户和凭证组的配置。
     */
    private AgentMcpUserCredential find(String userId, String credentialGroup) {
        AgentMcpUserCredential credential = credentialIService.lambdaQuery()
                .eq(AgentMcpUserCredential::getUserId, userId)
                .eq(AgentMcpUserCredential::getCredentialGroup, credentialGroup)
                .one();
        if (credential != null && StringUtil.isBlank(credential.getCredentialValue())) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        return credential;
    }

    /**
     * 按服务配置声明的位置注入用户凭证，保留数据库中的其他公共预设配置。
     */
    private McpServer inject(McpServer runtimeServer, AgentMcpServer server, String credential) {
        McpCredentialPlacement placement = server.getCredentialPlacement();
        String credentialKey = server.getCredentialKey();
        if (placement == null || StringUtil.isBlank(credentialKey)) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_CONFIG_ERROR);
        }
        String key = credentialKey.trim();
        return switch (placement) {
            case HTTP_HEADER -> runtimeServer.setHeaders(merge(runtimeServer.getHeaders(), key, credential, true,
                    server.getTransportType() == McpTransportType.STREAMABLE_HTTP));
            case QUERY_PARAMETER -> runtimeServer.setQueryParameters(merge(runtimeServer.getQueryParameters(), key,
                    credential, false, server.getTransportType() == McpTransportType.STREAMABLE_HTTP));
            case ENVIRONMENT_VARIABLE -> runtimeServer.setEnvironment(merge(runtimeServer.getEnvironment(), key,
                    credential, false, server.getTransportType() == McpTransportType.STDIO));
        };
    }

    /**
     * 合并用户凭证并校验注入位置与传输类型一致。
     */
    private Map<String, String> merge(Map<String, String> preset, String key, String credential,
                                      boolean caseInsensitiveKey, boolean compatibleTransport) {
        if (!compatibleTransport) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_CONFIG_ERROR);
        }
        Map<String, String> values = new LinkedHashMap<>(preset);
        if (caseInsensitiveKey) {
            values.keySet().removeIf(existingKey -> key.equalsIgnoreCase(existingKey));
        }
        values.put(key, credential);
        return Map.copyOf(values);
    }

    /**
     * 规范化用户凭证并拒绝换行注入。
     */
    private String normalize(String credential) {
        String value = StringUtil.nullToEmpty(credential).trim();
        if (StringUtil.isBlank(value) || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        return value;
    }
}
