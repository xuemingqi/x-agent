package com.x.ai.mcp.api.config;

import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.JsonUtil;
import feign.Response;
import feign.codec.ErrorDecoder;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 保留 MCP Runtime 的业务错误，远程响应内容不进入业务异常消息。
 */
public class McpRuntimeApiFeignConfiguration implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        byte[] body = readBody(response);
        ResponseCodeEnum responseCode = resolveResponseCode(body);
        if (responseCode != null && responseCode != ResponseCodeEnum.SUCCESS) {
            return new XException(responseCode);
        }
        return defaultDecoder.decode(methodKey, response.toBuilder().body(body).build());
    }

    /**
     * 读取可重放的响应体，读取失败时交给 Feign 默认状态码处理。
     */
    private byte[] readBody(Response response) {
        if (response.body() == null) {
            return new byte[0];
        }
        try {
            return response.body().asInputStream().readAllBytes();
        } catch (IOException error) {
            return new byte[0];
        }
    }

    /**
     * 只接受本系统定义的业务响应码，不直接使用远程异常文本。
     */
    private ResponseCodeEnum resolveResponseCode(byte[] body) {
        if (body.length == 0) {
            return null;
        }
        try {
            JsonNode result = JsonUtil.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode code = result == null ? null : result.get("code");
            return code == null || !code.isInt() ? null : ResponseCodeEnum.fromCode(code.intValue()).orElse(null);
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }
}
