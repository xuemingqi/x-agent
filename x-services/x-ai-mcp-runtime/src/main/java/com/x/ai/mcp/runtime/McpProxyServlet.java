package com.x.ai.mcp.runtime;

import com.x.framework.common.exception.XBaseException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class McpProxyServlet extends HttpServlet {

    private static final String PATH_SEPARATOR = "/";

    private static final int TARGET_PATH_SEGMENT_COUNT = 2;

    private final McpRuntimeRegistry runtimeRegistry;


    public McpProxyServlet(McpRuntimeRegistry runtimeRegistry) {
        this.runtimeRegistry = runtimeRegistry;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        RuntimeTarget target = runtimeTarget(request.getPathInfo());
        if (target == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        try {
            runtimeRegistry.get(target.userId(), target.configurationId()).service(request, response);
        } catch (XBaseException error) {
            response.sendError(error.getResponseCodeEnum().getHttpStatus().value());
        } catch (RuntimeException error) {
            log.warn("MCP proxy request failed, configurationId={}", target.configurationId(), error);
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY);
        }
    }

    /**
     * PathInfo 已由 Servlet 容器解码，直接解析以保留用户 ID 中的加号和百分号。
     */
    private RuntimeTarget runtimeTarget(String pathInfo) {
        if (pathInfo == null || !pathInfo.startsWith(PATH_SEPARATOR)) {
            return null;
        }
        String[] segments = pathInfo.substring(PATH_SEPARATOR.length()).split(PATH_SEPARATOR, -1);
        if (segments.length != TARGET_PATH_SEGMENT_COUNT || segments[0].isBlank() || segments[1].isBlank()) {
            return null;
        }
        try {
            return new RuntimeTarget(segments[0], Long.valueOf(segments[1]));
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private record RuntimeTarget(String userId, Long configurationId) {
    }
}
