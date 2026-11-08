package com.x.framework.core.system;

import com.x.framework.common.utils.JsonUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * @author : xuemingqi
 * @since : 2025/12/24 15:42
 */
@Configuration
public class SystemConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return JsonUtil.OBJECT_MAPPER;
    }
}
