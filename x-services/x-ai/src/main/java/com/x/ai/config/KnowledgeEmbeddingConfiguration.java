package com.x.ai.config;

import com.x.ai.client.OllamaEmbeddingClient;
import com.x.ai.properties.KnowledgeEmbeddingProperties;
import feign.Feign;
import feign.Request;
import feign.Retryer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 单独构建 Ollama Feign 客户端，不转发平台 token，也不记录文档请求正文。
 */
@Configuration
public class KnowledgeEmbeddingConfiguration {

    @Bean
    public OllamaEmbeddingClient ollamaEmbeddingClient(KnowledgeEmbeddingProperties properties,
                                                       ObjectProvider<FeignHttpMessageConverters> converters) {
        return Feign.builder()
                .contract(new SpringMvcContract())
                .encoder(new SpringEncoder(converters))
                .decoder(new SpringDecoder(converters))
                .options(new Request.Options(5, TimeUnit.SECONDS, properties.getTimeoutSeconds(), TimeUnit.SECONDS, true))
                .retryer(Retryer.NEVER_RETRY)
                .target(OllamaEmbeddingClient.class, properties.getBaseUrl());
    }
}
