package com.x.ai.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@Data
@ConfigurationProperties("mcp.runtime")
public class McpRuntimeProperties {

    private static final String ENDPOINT_PATH_SEPARATOR = "/";

    /**
     * 应用内 MCP Streamable HTTP 代理端点，不包含 Servlet context-path。
     */
    private String proxyEndpoint = "/internal/mcp";

    /**
     * MCP 初始化和请求外层等待增加的缓冲时间，单位秒。
     */
    private long timeoutBufferSeconds = 1L;

    /**
     * STDIO 包管理器持久化缓存根目录。
     */
    private Path dataDirectory = Path.of(".x-ai-mcp");

    /**
     * 统一移除尾部分隔符，确保 Servlet 映射与用户级 SDK 端点一致。
     */
    public void setProxyEndpoint(String proxyEndpoint) {
        this.proxyEndpoint = StringUtils.trimTrailingCharacter(proxyEndpoint, '/');
    }

    /**
     * 返回指定用户和 MCP 配置的应用内代理路径，不包含 Servlet context-path。
     *
     * @param userId          用户 ID
     * @param configurationId MCP 配置 ID
     * @return MCP 代理路径
     */
    public String runtimePath(String userId, Long configurationId) {
        String encodedUserId = URLEncoder.encode(userId, StandardCharsets.UTF_8).replace("+", "%20");
        return proxyEndpoint + ENDPOINT_PATH_SEPARATOR + encodedUserId + ENDPOINT_PATH_SEPARATOR + configurationId;
    }
}
