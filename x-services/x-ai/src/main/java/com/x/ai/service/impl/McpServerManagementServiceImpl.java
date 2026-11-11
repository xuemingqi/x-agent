package com.x.ai.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.JsonUtil;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.common.domain.McpConnectionTestDo;
import com.x.ai.common.domain.McpServerDo;
import com.x.ai.common.dto.McpServerCreateDto;
import com.x.ai.common.dto.McpServerUpdateDto;
import com.x.ai.common.enums.McpTransportType;
import com.x.ai.converter.McpServerConverter;
import com.x.ai.db.entity.AgentMcpServer;
import com.x.ai.db.service.AgentMcpServerIService;
import com.x.ai.common.model.McpServer;
import com.x.ai.service.McpClientService;
import com.x.ai.service.McpServerManagementService;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class McpServerManagementServiceImpl implements McpServerManagementService {

    private static final long CONNECTION_TEST_TIMEOUT_BUFFER_SECONDS = 1L;

    private static final int MAX_NAME_LENGTH = 64;

    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private static final int MAX_REQUEST_TIMEOUT_SECONDS = 600;

    private static final int MAX_INITIALIZATION_TIMEOUT_SECONDS = 120;

    private final AgentMcpServerIService agentMcpServerIService;

    private final Snowflake snowflake;

    private final McpServerConverter mcpServerConverter;

    private final McpClientService mcpClientService;

    @Override
    public List<McpServer> listRuntimeServers(String userId) {
        return agentMcpServerIService.ownedQuery(userId)
                .eq(AgentMcpServer::getEnabled, true)
                .eq(AgentMcpServer::getTransportType, McpTransportType.STREAMABLE_HTTP)
                .orderByAsc(AgentMcpServer::getId)
                .list().stream().map(mcpServerConverter::convert).toList();
    }

    @Override
    public List<McpServerDo> list(String userId) {
        return agentMcpServerIService.ownedQuery(userId).orderByAsc(AgentMcpServer::getName)
                .list().stream().map(this::toMcpServerDo).toList();
    }

    @Override
    public McpServerDo get(String userId, Long id) {
        return toMcpServerDo(ownedServer(userId, id));
    }

    @Override
    public boolean setEnabled(String userId, Long id, boolean enabled) {
        ownedServer(userId, id);
        if (!agentMcpServerIService.lambdaUpdate().eq(AgentMcpServer::getOwnerUserId, userId)
                .eq(AgentMcpServer::getId, id).set(AgentMcpServer::getEnabled, enabled).update()) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_NOT_FOUND);
        }
        return enabled;
    }

    @Override
    public McpServerDo create(String userId, McpServerCreateDto request) {
        String name = normalizeName(request.getName());
        ensureNameAvailable(userId, name);

        AgentMcpServer server = new AgentMcpServer()
                .setId(snowflake.nextId())
                .setName(name)
                .setOwnerUserId(userId);
        applyConfiguration(server, request.getDescription(), request.getTransportType(), request.getEndpoint(),
                request.getHeaders(), request.getQueryParameters(), request.getEnabled(), request.getRequestTimeoutSeconds(),
                request.getInitializationTimeoutSeconds(), new McpServer());
        save(server);
        return toMcpServerDo(server);
    }

    @Override
    public McpServerDo update(String userId, Long id, McpServerUpdateDto request) {
        AgentMcpServer server = ownedServer(userId, id);
        applyConfiguration(server, request.getDescription(), request.getTransportType(), request.getEndpoint(),
                request.getHeaders(), request.getQueryParameters(), request.getEnabled(), request.getRequestTimeoutSeconds(),
                request.getInitializationTimeoutSeconds(), mcpServerConverter.convert(server));
        if (!agentMcpServerIService.updateById(server)) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        return toMcpServerDo(server);
    }

    @Override
    public boolean delete(String userId, Long id) {
        AgentMcpServer server = ownedServer(userId, id);
        return agentMcpServerIService.removeById(server.getId());
    }

    @Override
    public McpConnectionTestDo testConnection(String userId, Long id) {
        AgentMcpServer server = ownedServer(userId, id);
        if (!server.getTransportType().isRuntimeSupported()) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_TRANSPORT_UNSUPPORTED);
        }
        McpServer mcpServer = mcpServerConverter.convert(server);
        McpClientWrapper client = null;
        try {
            client = mcpClientService.create(mcpServer);
            List<McpSchema.Tool> tools = initializeAndListTools(client, connectionTestTimeout(mcpServer));
            List<String> toolNames = CollectionUtils.isEmpty(tools) ? List.of() : tools.stream()
                    .map(McpSchema.Tool::name)
                    .sorted()
                    .toList();
            return new McpConnectionTestDo()
                    .setConnected(true)
                    .setToolNames(toolNames);
        } catch (XException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_CONNECTION_ERROR);
        } finally {
            closeClient(client);
        }
    }

    /**
     * 规范化工具命名空间并校验数据库长度约束。
     */
    private String normalizeName(String name) {
        String normalized = StringUtil.trim(name);
        if (StringUtil.isBlank(normalized) || normalized.length() > MAX_NAME_LENGTH) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        return normalized;
    }

    /**
     * 保存配置并转换并发名称冲突。
     */
    private void save(AgentMcpServer server) {
        try {
            if (!agentMcpServerIService.save(server)) {
                throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
            }
        } catch (DuplicateKeyException error) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_CONFLICT);
        }
    }

    /**
     * 查询当前用户拥有的个人 MCP 配置。
     */
    private AgentMcpServer ownedServer(String userId, Long id) {
        AgentMcpServer server = agentMcpServerIService.ownedQuery(userId)
                .eq(AgentMcpServer::getId, id)
                .one();
        if (server == null) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_NOT_FOUND);
        }
        return server;
    }

    /**
     * 保证同一用户下的 MCP 工具命名空间唯一。
     */
    private void ensureNameAvailable(String userId, String name) {
        long count = agentMcpServerIService.ownedQuery(userId)
                .eq(AgentMcpServer::getName, name.trim())
                .count();
        if (count > 0) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_CONFLICT);
        }
    }

    /**
     * 校验并应用传输配置
     */
    private void applyConfiguration(AgentMcpServer server, String description, McpTransportType transportType,
                                    String endpoint, Map<String, String> headers,
                                    Map<String, String> queryParameters, Boolean enabled,
                                    Integer requestTimeoutSeconds, Integer initializationTimeoutSeconds, McpServer currentServer) {
        if (StringUtil.isBlank(description) || description.trim().length() > MAX_DESCRIPTION_LENGTH
                || enabled == null || requestTimeoutSeconds == null || requestTimeoutSeconds < 1
                || requestTimeoutSeconds > MAX_REQUEST_TIMEOUT_SECONDS || initializationTimeoutSeconds == null
                || initializationTimeoutSeconds < 1 || initializationTimeoutSeconds > MAX_INITIALIZATION_TIMEOUT_SECONDS) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        if (transportType != McpTransportType.STREAMABLE_HTTP) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_TRANSPORT_UNSUPPORTED);
        }
        applyStreamableHttpConfiguration(server, endpoint, headers, queryParameters, currentServer);
        server.setDescription(description.trim())
                .setTransportType(transportType)
                .setRequestTimeoutSeconds(requestTimeoutSeconds)
                .setInitializationTimeoutSeconds(initializationTimeoutSeconds)
                .setEnabled(enabled);
    }

    /**
     * 应用 Streamable HTTP 配置。
     */
    private void applyStreamableHttpConfiguration(AgentMcpServer server, String endpoint, Map<String, String> headers,
                                                  Map<String, String> queryParameters, McpServer currentServer) {
        URI endpointUri = parseEndpointUri(endpoint);
        Map<String, String> requestedQueryParameters =
                mergeEndpointQueryParameters(parseQueryParameters(endpointUri.getRawQuery()), queryParameters);
        server.setEndpoint(endpointWithoutQuery(endpoint.trim()))
                .setHeadersJson(toMapJson(mergeCredentialValues(headers, currentServer.getHeaders())))
                .setQueryParametersJson(toMapJson(mergeCredentialValues(requestedQueryParameters, currentServer.getQueryParameters())));
    }

    /**
     * 合并不回显的凭证字段；已存在 key 的空值表示保留，未提交的 key 表示删除。
     */
    private Map<String, String> mergeCredentialValues(Map<String, String> requested, Map<String, String> current) {
        Map<String, String> normalized = normalizeCredentialValues(requested);
        Map<String, String> existing = current == null ? Map.of() : current;
        Map<String, String> merged = new LinkedHashMap<>();
        normalized.forEach((key, value) -> {
            if (StringUtil.isBlank(value)) {
                String preserved = findValueIgnoreCase(existing, key);
                if (preserved == null) {
                    throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
                }
                merged.put(key, preserved);
            } else {
                merged.put(key, value);
            }
        });
        return Map.copyOf(merged);
    }

    /**
     * 规范化凭证键值，并按大小写不敏感规则拒绝重复名称。
     */
    private Map<String, String> normalizeCredentialValues(Map<String, String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        Set<String> normalizedKeys = new LinkedHashSet<>();
        values.forEach((key, value) -> {
            String cleanKey = StringUtil.nullToEmpty(key).trim();
            String cleanValue = StringUtil.nullToEmpty(value).trim();
            if (StringUtil.isBlank(cleanKey) || containsLineBreak(cleanKey) || containsLineBreak(cleanValue)
                    || !normalizedKeys.add(cleanKey.toLowerCase(Locale.ROOT))) {
                throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
            }
            normalized.put(cleanKey, cleanValue);
        });
        return Map.copyOf(normalized);
    }

    /**
     * 解析并校验 Streamable HTTP 地址。
     */
    private URI parseEndpointUri(String endpoint) {
        if (StringUtil.isBlank(endpoint)) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        try {
            URI uri = URI.create(endpoint.trim());
            String scheme = uri.getScheme();
            if ((!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawFragment() != null) {
                throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
            }
            return uri;
        } catch (IllegalArgumentException error) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
    }

    /**
     * 去除 Endpoint 查询字符串，鉴权参数由独立字段持久化。
     */
    private String endpointWithoutQuery(String endpoint) {
        int queryIndex = endpoint.indexOf('?');
        return queryIndex < 0 ? endpoint : endpoint.substring(0, queryIndex);
    }

    /**
     * 解析 URL 查询字符串并保留每个鉴权参数的完整值。
     */
    private Map<String, String> parseQueryParameters(String rawQuery) {
        if (StringUtil.isBlank(rawQuery)) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&", -1)) {
            int separator = pair.indexOf('=');
            String key = decodeQueryPart(separator < 0 ? pair : pair.substring(0, separator));
            String value = decodeQueryPart(separator < 0 ? "" : pair.substring(separator + 1));
            if (StringUtil.isBlank(key) || findValueIgnoreCase(values, key) != null) {
                throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
            }
            values.put(key, value);
        }
        return Map.copyOf(values);
    }

    /**
     * 合并 Endpoint 自带参数和独立参数，同名参数只能保留一个确定值。
     */
    private Map<String, String> mergeEndpointQueryParameters(Map<String, String> endpointValues, Map<String, String> requestedValues) {
        Map<String, String> merged = new LinkedHashMap<>(endpointValues);
        normalizeCredentialValues(requestedValues).forEach((key, value) -> {
            String endpointValue = findValueIgnoreCase(merged, key);
            if (endpointValue == null) {
                merged.put(key, value);
            } else if (!StringUtil.isBlank(value) && !endpointValue.equals(value)) {
                throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
            }
        });
        return Map.copyOf(merged);
    }

    /**
     * URL Decode 单个查询参数片段。
     */
    private String decodeQueryPart(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
    }

    /**
     * 按大小写不敏感规则查找已有凭证值。
     */
    private String findValueIgnoreCase(Map<String, String> values, String key) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 判断字符串是否包含 HTTP 协议不允许的换行。
     */
    private boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    /**
     * 将凭证键值序列化为数据库明文 JSON。
     */
    private String toMapJson(Map<String, String> values) {
        String json = JsonUtil.toJsonStr(values == null ? Map.of() : values);
        if (StringUtil.isEmpty(json)) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_CONFIG_ERROR);
        }
        return json;
    }

    /**
     * 返回当前用户的配置；凭证只提供字段名用于编辑。
     */
    private McpServerDo toMcpServerDo(AgentMcpServer server) {
        McpServer configuration = mcpServerConverter.convert(server);
        return new McpServerDo()
                .setId(server.getId()).setName(server.getName()).setDescription(server.getDescription())
                .setTransportType(server.getTransportType()).setEndpoint(configuration.getEndpoint())
                .setHeaders(hiddenCredentialValues(configuration.getHeaders()))
                .setQueryParameters(hiddenCredentialValues(configuration.getQueryParameters()))
                .setEnabled(configuration.isEnabled()).setRuntimeSupported(server.getTransportType().isRuntimeSupported())
                .setRequestTimeoutSeconds(server.getRequestTimeoutSeconds())
                .setInitializationTimeoutSeconds(server.getInitializationTimeoutSeconds());
    }

    /**
     * 仅返回个人 MCP 凭证 key，所有 value 均为空字符串。
     */
    private Map<String, String> hiddenCredentialValues(Map<String, String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return Map.of();
        }
        Map<String, String> hidden = new LinkedHashMap<>();
        values.keySet().stream().sorted().forEach(key -> hidden.put(key, ""));
        return Map.copyOf(hidden);
    }

    /**
     * 计算连接检测的外层等待上限。
     */
    private Duration connectionTestTimeout(McpServer server) {
        long seconds = (long) server.getInitializationTimeoutSeconds() + server.getRequestTimeoutSeconds()
                + CONNECTION_TEST_TIMEOUT_BUFFER_SECONDS;
        return Duration.ofSeconds(seconds);
    }

    /**
     * 初始化完成后再创建工具列表请求，避免 AgentScope 在 Reactor 组装阶段检查到未初始化状态。
     */
    private List<McpSchema.Tool> initializeAndListTools(McpClientWrapper client, Duration timeout) {
        return client.initialize()
                .then(Mono.defer(client::listTools))
                .block(timeout);
    }

    /**
     * 安全关闭连接检测使用的临时客户端。
     */
    private void closeClient(McpClientWrapper client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (RuntimeException error) {
            log.warn("Failed to close temporary MCP client, errorType={}", error.getClass().getSimpleName());
        }
    }

}
