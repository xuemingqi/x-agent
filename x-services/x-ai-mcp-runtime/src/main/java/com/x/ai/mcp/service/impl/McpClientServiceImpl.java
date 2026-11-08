package com.x.ai.mcp.service.impl;

import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.ai.mcp.api.enums.McpTransportType;
import com.x.ai.mcp.config.McpRuntimeProperties;
import com.x.ai.mcp.model.McpServer;
import com.x.ai.mcp.service.McpClientService;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class McpClientServiceImpl implements McpClientService {

    private static final String NPM_CACHE_DIRECTORY = "npm-cache";

    private static final String UV_CACHE_DIRECTORY = "uv-cache";

    private static final String PIP_CACHE_DIRECTORY = "pip-cache";

    private static final String NPM_CACHE_ENVIRONMENT_VARIABLE = "NPM_CONFIG_CACHE";

    private static final String UV_CACHE_ENVIRONMENT_VARIABLE = "UV_CACHE_DIR";

    private static final String PIP_CACHE_ENVIRONMENT_VARIABLE = "PIP_CACHE_DIR";

    private final McpRuntimeProperties properties;


    @Override
    public McpClientWrapper create(McpServer server) {
        if (server == null || server.getTransportType() == null) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_TRANSPORT_UNSUPPORTED);
        }
        McpClientBuilder builder = McpClientBuilder.create(server.getName())
                .timeout(Duration.ofSeconds(server.getRequestTimeoutSeconds()))
                .initializationTimeout(Duration.ofSeconds(server.getInitializationTimeoutSeconds()));
        if (server.getTransportType() == McpTransportType.STREAMABLE_HTTP) {
            builder.streamableHttpTransport(server.getEndpoint())
                    .headers(server.getHeaders())
                    .queryParams(server.getQueryParameters());
        } else {
            builder.stdioTransport(server.getCommand(), server.getArguments(), stdioEnvironment(server));
        }
        McpClientWrapper client = builder
                .buildAsync()
                .block();
        if (client == null) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_CONNECTION_ERROR);
        }
        return client;
    }

    /**
     * 合并 STDIO 私有环境变量和持久化包管理器缓存目录。
     */
    private Map<String, String> stdioEnvironment(McpServer server) {
        Path dataDirectory = properties.getDataDirectory().toAbsolutePath().normalize();
        Path npmCache = createDirectory(dataDirectory.resolve(NPM_CACHE_DIRECTORY));
        Path uvCache = createDirectory(dataDirectory.resolve(UV_CACHE_DIRECTORY));
        Path pipCache = createDirectory(dataDirectory.resolve(PIP_CACHE_DIRECTORY));

        Map<String, String> environment = new LinkedHashMap<>(server.getEnvironment());
        environment.put(NPM_CACHE_ENVIRONMENT_VARIABLE, npmCache.toString());
        environment.put(UV_CACHE_ENVIRONMENT_VARIABLE, uvCache.toString());
        environment.put(PIP_CACHE_ENVIRONMENT_VARIABLE, pipCache.toString());
        return Map.copyOf(environment);
    }

    /**
     * 创建并返回持久化缓存目录，目录不可用时阻止启动 STDIO 进程。
     */
    private Path createDirectory(Path directory) {
        try {
            return Files.createDirectories(directory);
        } catch (IOException error) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_CONFIG_ERROR);
        }
    }
}
