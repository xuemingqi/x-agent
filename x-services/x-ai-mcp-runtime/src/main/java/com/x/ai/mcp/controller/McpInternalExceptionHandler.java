package com.x.ai.mcp.controller;

import com.x.framework.common.exception.XBaseException;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 内部 MCP 接口用 HTTP 状态承载业务失败，供 Feign 还原错误；平台 Web 响应约定保持不变。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {McpInternalController.class, PublicMcpInternalController.class})
public class McpInternalExceptionHandler {

    @ExceptionHandler(XBaseException.class)
    public ResponseEntity<BaseResult<Void>> handleBusinessException(XBaseException exception) {
        return ResponseEntity.status(exception.getResponseCodeEnum().getHttpStatus())
                .body(ResultUtil.buildResultError(exception.getResponseCodeEnum()));
    }
}
