package com.x.api.ai.api.config;

import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.utils.ServletUtil;
import com.x.framework.common.utils.StringUtil;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * 外部 Agent Feign 调用配置。
 *
 * @author xuemingqi
 */
public class ExternalChatApiFeignConfiguration {

    /**
     * 将当前 MCP HTTP 请求中的 API Key 透传给 x-ai。
     */
    @Bean
    public RequestInterceptor externalChatApiKeyInterceptor() {
        return requestTemplate -> {
            String apiKey = ServletUtil.getHeader(CommonConstant.API_KEY_HEADER);
            if (StringUtil.isBlank(apiKey)) {
                throw new IllegalArgumentException("缺少 " + CommonConstant.API_KEY_HEADER + " 请求头");
            }
            requestTemplate.header(CommonConstant.API_KEY_HEADER, apiKey);
        };
    }
}
