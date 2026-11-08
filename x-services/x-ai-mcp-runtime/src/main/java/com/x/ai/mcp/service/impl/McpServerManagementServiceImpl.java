package com.x.ai.mcp.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.JsonUtil;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.mcp.api.domain.McpConnectionTestDo;
import com.x.ai.mcp.api.domain.McpServerDo;
import com.x.ai.mcp.api.dto.McpUserCredentialUpdateDto;
import com.x.ai.mcp.api.dto.McpServerCreateDto;
import com.x.ai.mcp.api.dto.McpServerUpdateDto;
import com.x.ai.mcp.api.dto.PublicMcpServerCreateDto;
import com.x.ai.mcp.api.dto.PublicMcpServerUpdateDto;
import com.x.ai.mcp.api.enums.McpScope;
import com.x.ai.mcp.api.enums.McpTransportType;
import com.x.ai.mcp.config.McpRuntimeProperties;
import com.x.ai.mcp.converter.McpServerConverter;
import com.x.ai.mcp.db.entity.AgentMcpServer;
import com.x.ai.mcp.db.service.AgentMcpServerIService;
import com.x.ai.mcp.model.McpServer;
import com.x.ai.mcp.runtime.McpRuntimeRegistry;
import com.x.ai.mcp.service.McpClientService;
import com.x.ai.mcp.service.McpServerManagementService;
import com.x.ai.mcp.service.McpUserCredentialService;
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
import java.util.Comparator;
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

    private static final int MAX_NAME_LENGTH = 64;

    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private static final int MAX_REQUEST_TIMEOUT_SECONDS = 600;

    private static final int MAX_INITIALIZATION_TIMEOUT_SECONDS = 120;

    private final AgentMcpServerIService agentMcpServerIService;

    private final Snowflake snowflake;

    private final McpServerConverter mcpServerConverter;

    private final McpClientService mcpClientService;

    private final McpRuntimeRegistry runtimeRegistry;

    private final McpUserCredentialService userCredentialService;

    private final McpRuntimeProperties properties;

    @Override
    public List<McpServerDo> listPublic() {
        return publicQuery().orderByAsc(AgentMcpServer::getScope).orderByAsc(AgentMcpServer::getName)
                .list().stream().map(this::toPublicManagementDo).toList();
    }

    @Override
    public McpServerDo getPublic(Long id) {
        return toPublicManagementDo(publicServer(id));
    }

    @Override
    public McpServerDo createPublic(PublicMcpServerCreateDto request) {
        if (request == null || request.getScope() != McpScope.PUBLIC
                && request.getScope() != McpScope.INTERNAL_PUBLIC) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        String name = normalizeName(request.getName());
        if (agentMcpServerIService.lambdaQuery().eq(AgentMcpServer::getName, name).exists()) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_CONFLICT);
        }
        AgentMcpServer server = new AgentMcpServer().setId(snowflake.nextId())
                .setName(name).setScope(request.getScope()).setOwnerUserId("");
        applyConfiguration(server, request.getDescription(), request.getTransportType(), request.getEndpoint(),
                request.getCommand(), request.getArguments(), request.getHeaders(), request.getQueryParameters(),
                request.getEnvironment(), request.getEnabled(), request.getRequestTimeoutSeconds(),
                request.getInitializationTimeoutSeconds(), new McpServer());
        save(server);
        return toPublicManagementDo(server);
    }

    @Override
    public McpServerDo updatePublic(Long id, PublicMcpServerUpdateDto request) {
        if (request == null) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        AgentMcpServer server = publicServer(id);
        applyConfiguration(server, request.getDescription(), request.getTransportType(), request.getEndpoint(),
                request.getCommand(), request.getArguments(), request.getHeaders(), request.getQueryParameters(),
                request.getEnvironment(), request.getEnabled(), request.getRequestTimeoutSeconds(),
                request.getInitializationTimeoutSeconds(), mcpServerConverter.convert(server));
        if (!agentMcpServerIService.updateById(server)) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        runtimeRegistry.evictConfiguration(id);
        return toPublicManagementDo(server);
    }

    @Override
    public boolean deletePublic(Long id) {
        AgentMcpServer server = publicServer(id);
        boolean deleted = agentMcpServerIService.removeById(server.getId());
        if (deleted) {
            runtimeRegistry.evictConfiguration(id);
        }
        return deleted;
    }


    @Override
    public List<McpServerDo> list(String userId, McpScope scope) {
        List<AgentMcpServer> servers = visibleQuery(userId, scope)
                .list()
                .stream()
                .sorted(Comparator.comparingInt((AgentMcpServer server) -> server.getScope().getCode())
                        .thenComparing(AgentMcpServer::getName))
                .toList();
        Set<Long> userCredentialRequiredServerIds = Set.copyOf(userCredentialServerIds(servers));
        Set<Long> configuredServerIds = userCredentialService.configuredServerIds(userId, servers);
        return servers.stream()
                .map(server -> toMcpServerDo(server, userCredentialRequiredServerIds.contains(server.getId()),
                        configuredServerIds.contains(server.getId())))
                .toList();
    }

    @Override
    public McpServerDo get(String userId, McpScope scope, Long id) {
        AgentMcpServer server = visibleServer(userId, scope, id);
        boolean userCredentialRequired = userCredentialService.requiresUserCredential(server);
        boolean userCredentialConfigured = userCredentialRequired
                && userCredentialService.configuredServerIds(userId, List.of(server)).contains(server.getId());
        return toMcpServerDo(server, userCredentialRequired, userCredentialConfigured);
    }

    @Override
    public McpServerDo create(String userId, McpServerCreateDto request) {
        String name = normalizeName(request.getName());
        ensureNameAvailable(userId, name);

        AgentMcpServer server = new AgentMcpServer()
                .setId(snowflake.nextId())
                .setName(name)
                .setScope(McpScope.USER)
                .setOwnerUserId(userId);
        applyConfiguration(server, request.getDescription(), request.getTransportType(), request.getEndpoint(),
                request.getCommand(), request.getArguments(), request.getHeaders(), request.getQueryParameters(),
                request.getEnvironment(), request.getEnabled(), request.getRequestTimeoutSeconds(),
                request.getInitializationTimeoutSeconds(), new McpServer());
        save(server);
        return toMcpServerDo(server, false, false);
    }

    @Override
    public McpServerDo update(String userId, Long id, McpServerUpdateDto request) {
        AgentMcpServer server = ownedServer(userId, id);
        applyConfiguration(server, request.getDescription(), request.getTransportType(), request.getEndpoint(),
                request.getCommand(), request.getArguments(), request.getHeaders(), request.getQueryParameters(),
                request.getEnvironment(), request.getEnabled(), request.getRequestTimeoutSeconds(),
                request.getInitializationTimeoutSeconds(), mcpServerConverter.convert(server));
        if (!agentMcpServerIService.updateById(server)) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        runtimeRegistry.evict(userId, id);
        return toMcpServerDo(server, false, false);
    }

    @Override
    public McpServerDo updateUserCredential(String userId, Long id, McpUserCredentialUpdateDto request) {
        AgentMcpServer server = visibleServer(userId, McpScope.INTERNAL_PUBLIC, id);
        if (!userCredentialService.requiresUserCredential(server)) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        String credentialGroup = userCredentialService.save(userId, server, request.getCredential());
        runtimeRegistry.evictCredentialGroup(userId, credentialGroup);
        return toMcpServerDo(server, true, true);
    }

    @Override
    public boolean delete(String userId, Long id) {
        AgentMcpServer server = ownedServer(userId, id);
        boolean deleted = agentMcpServerIService.removeById(server.getId());
        if (deleted) {
            runtimeRegistry.evict(userId, id);
        }
        return deleted;
    }

    @Override
    public McpConnectionTestDo testConnection(String userId, McpScope scope, Long id) {
        AgentMcpServer server = visibleServer(userId, scope, id);
        if (!server.getTransportType().isRuntimeSupported()) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_TRANSPORT_UNSUPPORTED);
        }
        McpServer mcpServer = userCredentialService.toRuntimeServer(userId, server);
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
     * 构建当前用户可见范围查询，禁止读取其他用户的私有配置。
     */
    private LambdaQueryChainWrapper<AgentMcpServer> visibleQuery(String userId, McpScope scope) {
        LambdaQueryChainWrapper<AgentMcpServer> query = agentMcpServerIService.visibleQuery(userId);
        if (scope == McpScope.PUBLIC) {
            query.in(AgentMcpServer::getScope, McpScope.PUBLIC, McpScope.INTERNAL_PUBLIC);
        } else if (scope != null) {
            query.eq(AgentMcpServer::getScope, scope);
        }
        return query;
    }

    /**
     * 公共管理入口仅操作共享配置。
     */
    private LambdaQueryChainWrapper<AgentMcpServer> publicQuery() {
        return agentMcpServerIService.lambdaQuery()
                .in(AgentMcpServer::getScope, McpScope.PUBLIC, McpScope.INTERNAL_PUBLIC);
    }

    /**
     * 校验公共配置存在，禁止通过管理入口编辑个人配置。
     */
    private AgentMcpServer publicServer(Long id) {
        AgentMcpServer server = publicQuery().eq(AgentMcpServer::getId, id).one();
        if (server == null) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_NOT_FOUND);
        }
        return server;
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
     * 查询当前用户可见且作用域匹配的 MCP 配置。
     */
    private AgentMcpServer visibleServer(String userId, McpScope scope, Long id) {
        if (scope == null) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        AgentMcpServer server = visibleQuery(userId, scope)
                .eq(AgentMcpServer::getId, id)
                .one();
        if (server == null) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_NOT_FOUND);
        }
        return server;
    }

    /**
     * 查询当前用户拥有的个人 MCP 配置。
     */
    private AgentMcpServer ownedServer(String userId, Long id) {
        AgentMcpServer server = agentMcpServerIService.lambdaQuery()
                .eq(AgentMcpServer::getId, id)
                .eq(AgentMcpServer::getScope, McpScope.USER)
                .eq(AgentMcpServer::getOwnerUserId, userId)
                .one();
        if (server == null) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_NOT_FOUND);
        }
        return server;
    }

    /**
     * 保证个人 MCP 名称不会与公共或当前用户已有命名空间冲突。
     */
    private void ensureNameAvailable(String userId, String name) {
        long count = visibleQuery(userId, null)
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
                                    String endpoint, String command, List<String> arguments, Map<String, String> headers,
                                    Map<String, String> queryParameters, Map<String, String> environment, Boolean enabled,
                                    Integer requestTimeoutSeconds, Integer initializationTimeoutSeconds, McpServer currentServer) {
        if (StringUtil.isBlank(description) || description.trim().length() > MAX_DESCRIPTION_LENGTH
                || enabled == null || requestTimeoutSeconds == null || requestTimeoutSeconds < 1
                || requestTimeoutSeconds > MAX_REQUEST_TIMEOUT_SECONDS || initializationTimeoutSeconds == null
                || initializationTimeoutSeconds < 1 || initializationTimeoutSeconds > MAX_INITIALIZATION_TIMEOUT_SECONDS) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        if (transportType == McpTransportType.STREAMABLE_HTTP) {
            applyStreamableHttpConfiguration(server, endpoint, headers, queryParameters, currentServer);
        } else if (transportType == McpTransportType.STDIO) {
            applyStdioConfiguration(server, command, arguments, environment, currentServer);
        } else {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        server.setDescription(description.trim())
                .setTransportType(transportType)
                .setRequestTimeoutSeconds(requestTimeoutSeconds)
                .setInitializationTimeoutSeconds(initializationTimeoutSeconds)
                .setEnabled(enabled);
    }

    /**
     * 应用 Streamable HTTP 配置并清除 STDIO 专属字段。
     */
    private void applyStreamableHttpConfiguration(AgentMcpServer server, String endpoint, Map<String, String> headers,
                                                  Map<String, String> queryParameters, McpServer currentServer) {
        URI endpointUri = parseEndpointUri(endpoint);
        Map<String, String> requestedQueryParameters =
                mergeEndpointQueryParameters(parseQueryParameters(endpointUri.getRawQuery()), queryParameters);
        server.setEndpoint(endpointWithoutQuery(endpoint.trim()))
                .setCommand(null)
                .setArgumentsJson("[]")
                .setHeadersJson(toMapJson(mergeCredentialValues(headers, currentServer.getHeaders())))
                .setQueryParametersJson(toMapJson(mergeCredentialValues(requestedQueryParameters, currentServer.getQueryParameters())))
                .setEnvironmentJson("{}");
    }

    /**
     * 应用 STDIO 配置并清除 Streamable HTTP 专属字段。
     */
    private void applyStdioConfiguration(AgentMcpServer server, String command, List<String> arguments,
                                         Map<String, String> environment, McpServer currentServer) {
        if (StringUtil.isBlank(command)) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        server.setEndpoint(null)
                .setCommand(command.trim())
                .setArgumentsJson(toArgumentsJson(arguments))
                .setHeadersJson("{}")
                .setQueryParametersJson("{}")
                .setEnvironmentJson(toMapJson(mergeCredentialValues(environment, currentServer.getEnvironment())));
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
     * 序列化 STDIO 参数，保留参数边界而不拼接 Shell 字符串。
     */
    private String toArgumentsJson(List<String> arguments) {
        List<String> values = CollectionUtils.isEmpty(arguments) ? List.of() : arguments.stream()
                .map(StringUtil::nullToEmpty)
                .toList();
        String json = JsonUtil.toJsonStr(values);
        if (StringUtil.isEmpty(json)) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_INVALID);
        }
        return json;
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
     * 提取待批量查询用户凭证状态的公司内部公共 MCP ID。
     */
    private List<Long> userCredentialServerIds(List<AgentMcpServer> servers) {
        return servers.stream()
                .filter(userCredentialService::requiresUserCredential)
                .map(AgentMcpServer::getId)
                .toList();
    }

    /**
     * 转换为不暴露凭证值和所有者字段的管理界面结构。
     */
    private McpServerDo toMcpServerDo(AgentMcpServer server, boolean userCredentialRequired,
                                      boolean userCredentialConfigured) {
        return toMcpServerDo(server, userCredentialRequired, userCredentialConfigured,
                server.getScope() == McpScope.USER);
    }

    /**
     * 公共管理返回完整可编辑配置，启用状态不受管理员个人凭据影响。
     */
    private McpServerDo toPublicManagementDo(AgentMcpServer server) {
        return toMcpServerDo(server, userCredentialService.requiresUserCredential(server), false, true)
                .setEnabled(Boolean.TRUE.equals(server.getEnabled()));
    }

    /**
     * 按操作入口控制配置字段可见性，凭证值在所有入口保持脱敏。
     */
    private McpServerDo toMcpServerDo(AgentMcpServer server, boolean userCredentialRequired,
                                      boolean userCredentialConfigured, boolean editable) {
        McpServer mcpServer = editable ? mcpServerConverter.convert(server) : new McpServer();
        return new McpServerDo()
                .setId(server.getId())
                .setName(server.getName())
                .setDescription(server.getDescription())
                .setScope(server.getScope())
                .setTransportType(server.getTransportType())
                .setEndpoint(editable ? mcpServer.getEndpoint() : null)
                .setCommand(editable ? mcpServer.getCommand() : null)
                .setArguments(editable ? mcpServer.getArguments() : List.of())
                .setHeaders(hiddenCredentialValues(mcpServer.getHeaders()))
                .setQueryParameters(hiddenCredentialValues(mcpServer.getQueryParameters()))
                .setEnvironment(hiddenCredentialValues(mcpServer.getEnvironment()))
                .setEnabled(Boolean.TRUE.equals(server.getEnabled())
                        && (!userCredentialRequired || userCredentialConfigured))
                .setUserCredentialConfigured(userCredentialConfigured)
                .setUserCredentialRequired(userCredentialRequired)
                .setEditable(editable)
                .setRuntimeSupported(server.getTransportType().isRuntimeSupported())
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
                + properties.getTimeoutBufferSeconds();
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
