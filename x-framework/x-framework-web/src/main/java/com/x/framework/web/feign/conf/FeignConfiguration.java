package com.x.framework.web.feign.conf;

import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.utils.IdUtil;
import com.x.framework.common.utils.StringUtil;
import feign.Logger;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.http.converter.autoconfigure.ClientHttpMessageConvertersCustomizer;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * @author ：xuemingqi
 * @since ：Created in 2023/1/10 17:16
 */
@Configuration
public class FeignConfiguration {

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    /**
     * 启动时初始化转换器，避免首次并发请求读取半初始化列表。
     */
    @Bean
    @ConditionalOnMissingBean
    public FeignHttpMessageConverters feignHttpMessageConverters(ObjectProvider<ClientHttpMessageConvertersCustomizer> customizers,
                                                                ObjectProvider<HttpMessageConverterCustomizer> cloudCustomizers) {
        FeignHttpMessageConverters converters = new FeignHttpMessageConverters(customizers, cloudCustomizers);
        converters.getConverters();
        return converters;
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return res -> {
            String traceId = getCurrentRequestHeader(CommonConstant.TRACE_ID);
            String token = getCurrentRequestHeader(CommonConstant.TOKEN);

            if (null != token) {
                res.header(CommonConstant.TOKEN, token);
            }
            if (StringUtil.isBlank(traceId)) {
                traceId = MDC.get(CommonConstant.TRACE_ID);
            }
            if (StringUtil.isBlank(traceId)) {
                traceId = IdUtil.randomAlphanumeric(10);
            }
            res.header(CommonConstant.TRACE_ID, traceId);
        };
    }

    private String getCurrentRequestHeader(String name) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getHeader(name);
        }
        return null;
    }
}
