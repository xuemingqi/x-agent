package com.x.ai.servlet;

import com.x.ai.service.ApiKeyService;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.exception.XBaseException;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import java.io.IOException;

/**
 * MCP Servlet 绕过 MVC 拦截器，在每个 HTTP 请求进入协议处理前验证 API Key。
 */
@RequiredArgsConstructor
public class McpServerServlet extends HttpServlet {

    private final ApiKeyService apiKeyService;

    private final HttpServletStatelessServerTransport transport;

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        UserInfo user;
        try {
            user = apiKeyService.authenticateApiKey(request.getHeader(CommonConstant.API_KEY_HEADER));
        } catch (XBaseException error) {
            response.sendError(error.getResponseCodeEnum().getHttpStatus().value());
            return;
        }
        request.setAttribute(CommonConstant.USER_ID, user.getUserId());
        transport.service(request, response);
    }
}
