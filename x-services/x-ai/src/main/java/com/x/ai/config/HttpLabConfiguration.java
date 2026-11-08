package com.x.ai.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class HttpLabConfiguration {

    @Bean(destroyMethod = "dispose")
    public ConnectionProvider httpLabConnections() {
        return ConnectionProvider.builder("http-lab").maxConnections(100).pendingAcquireMaxCount(100)
                .pendingAcquireTimeout(Duration.ofSeconds(5)).maxIdleTime(Duration.ofSeconds(30))
                .evictInBackground(Duration.ofSeconds(30)).build();
    }

    @Bean
    public WebClient httpLabWebClient(ConnectionProvider httpLabConnections) {
        HttpClient client = HttpClient.create(httpLabConnections).followRedirect(false).disableRetry(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(client)).build();
    }
}
