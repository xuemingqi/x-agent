package com.x.framework.web.logback;

import feign.Logger;
import feign.Request;
import feign.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Keep credentials in headers, query strings and response bodies out of logs at every Feign level. */
@Component
@Slf4j
public class FeignLogger extends Logger {
    @Override
    protected void log(String configKey, String format, Object... args) {
        // Feign payload logging is deliberately replaced by the metadata hooks below.
    }

    @Override
    protected void logRequest(String configKey, Level logLevel, Request request) {
        log.info("client:[{}],method:[{}]", configKey, request.httpMethod());
    }

    @Override
    protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime) {
        log.info("client:[{}],status:[{}],durationMs:[{}]", configKey, response.status(), elapsedTime);
        return response;
    }
}
