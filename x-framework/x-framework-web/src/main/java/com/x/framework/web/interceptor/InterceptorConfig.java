package com.x.framework.web.interceptor;

import com.x.framework.common.auth.ApiKeyAuthenticationClient;
import com.x.framework.common.auth.AuthClient;
import com.x.framework.common.auth.InternalRequestSigner;
import org.springframework.beans.factory.annotation.Value;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Objects;

/**
 * @author ：xuemingqi
 * @since ：created in 2023/1/10 17:30
 */
@Configuration
@RefreshScope
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InterceptorConfig {

    @Bean
    public WebMvcConfigurer addInterceptors(AuthInterceptor authInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                Objects.requireNonNull(registry)
                        .addInterceptor(authInterceptor)
                        .addPathPatterns("/**")
                        .excludePathPatterns("/error")
                        //swagger
                        .excludePathPatterns("/swagger**/**", "/v3/**", "/webjars/**", "/api/**");
            }
        };
    }


    @Bean
    public AuthInterceptor authenticationInterceptor(AuthClient authClient,
                                                     ApiKeyAuthenticationClient apiKeyAuthenticationClient,
                                                     @Value("${x.security.internal-secret}") String secret) {
        return new AuthInterceptor(authClient, apiKeyAuthenticationClient, new InternalRequestSigner(secret));
    }

    @Bean
    @ConditionalOnMissingBean(ApiKeyAuthenticationClient.class)
    public ApiKeyAuthenticationClient apiKeyAuthenticationClient() {
        return apiKey -> {
            throw new XException(ResponseCodeEnum.API_KEY_INVALID);
        };
    }
}
