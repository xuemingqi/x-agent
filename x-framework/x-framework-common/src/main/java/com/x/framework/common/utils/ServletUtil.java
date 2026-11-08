package com.x.framework.common.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

/** Request headers and client IP metadata used by HTTP authentication and tracing. */
public final class ServletUtil {
    private static final String[] IP_HEADERS = {"X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP", "x-forwarded-for"};

    private ServletUtil() {}

    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return Objects.requireNonNull(attributes).getRequest();
    }

    public static String getHeader(String key) {
        return getRequest().getHeader(key);
    }

    public static String getRemoteIP() {
        HttpServletRequest request = getRequest();
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.trim().isEmpty()) {
                return ip.trim();
            }
        }
        return request.getRemoteAddr();
    }

    public static String getRemoteIP(HttpHeaders headers) {
        for (String header : IP_HEADERS) {
            String ip = headers.getFirst(header);
            if (ip != null && !ip.trim().isEmpty()) {
                return ip.trim();
            }
        }
        return null;
    }
}
