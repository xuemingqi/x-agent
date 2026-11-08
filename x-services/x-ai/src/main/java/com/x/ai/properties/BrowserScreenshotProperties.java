package com.x.ai.properties;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "agent.browser-screenshot")
public class BrowserScreenshotProperties {

    /**
     * 用户授权、截图和上传的总等待时间，单位为秒。
     */
    @Min(1)
    private int requestTimeoutSeconds = 180;

    /**
     * 等待截图等长工具调用期间的流式心跳间隔，单位为秒。
     */
    @Min(1)
    private int heartbeatIntervalSeconds = 15;

    /**
     * AI 提供的截图检查目的允许的最大字符数。
     */
    @Min(1)
    private int maxReasonLength = 1000;
}
