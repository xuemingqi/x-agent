package com.x.ai.mcp.gateway;

import com.x.api.ai.api.ExternalChatApi;
import com.x.api.ai.dto.ExternalAgentRequestDto;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=classpath:/mcp-gateway-test.yaml",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "spring.ai.mcp.server.protocol=STREAMABLE",
        "x.security.internal-secret=test-only-internal-signing-secret-32-chars"
})
class McpGatewayApplicationTest {
    private static HttpServer upstream;
    private static final AtomicReference<String> observedApiKey = new AtomicReference<>();

    @DynamicPropertySource
    static void configureUpstream(DynamicPropertyRegistry registry) throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/ai/open/chat/run", exchange -> {
            observedApiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"code\":200,\"data\":{\"text\":\"ok\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        upstream.start();
        registry.add("spring.cloud.openfeign.client.config.external-chat-api.url",
                () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
    }

    @AfterAll
    static void stopUpstream() {
        if (upstream != null) {
            upstream.stop(0);
        }
    }

    @Autowired
    private ExternalChatApi externalChatApi;

    @Test
    void startsWithApiModuleAutoConfigurationWithoutDuplicateFeignClients() {
        assertThat(externalChatApi).isNotNull();
    }

    @Test
    void callsTheAiContextPathAndForwardsTheApiKey() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "xai_test-only-key");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertThat(externalChatApi.run(new ExternalAgentRequestDto().setQuestion("hello")).getCode())
                    .isEqualTo(200);
            assertThat(observedApiKey.get()).isEqualTo("xai_test-only-key");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
