package com.x.api.ai.api.autoconfigure;

import com.x.api.ai.api.ExternalChatApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;

@AutoConfiguration
@ConditionalOnClass(FeignClient.class)
@EnableFeignClients(clients = ExternalChatApi.class)
public class XAiApiFeignAutoConfiguration {
}
