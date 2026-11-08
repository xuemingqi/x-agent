package com.x.ai.app.service.impl;

import com.x.ai.common.dto.HttpLoadTestDto;
import com.x.ai.common.enums.HttpLoadStatus;
import com.x.ai.common.model.HttpRequestField;
import com.x.ai.common.model.HttpRequestSpec;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.ai.config.HttpLabConfiguration;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class HttpRequestExecutionServiceImplTest {

    private DisposableServer server;

    private ConnectionProvider connections;

    private HttpRequestExecutionServiceImpl service;

    private final AtomicReference<String> receivedUri = new AtomicReference<>();

    private final AtomicReference<String> receivedHeader = new AtomicReference<>();

    private final AtomicReference<String> receivedToken = new AtomicReference<>();

    private final AtomicReference<String> receivedCookie = new AtomicReference<>();

    private final AtomicReference<String> receivedAuthorization = new AtomicReference<>();

    private final AtomicInteger redirected = new AtomicInteger();

    @BeforeEach
    void setUp() {
        server = HttpServer.create().host("127.0.0.1").port(0).route(routes -> routes
                .post("/echo", (request, response) -> {
                    receivedUri.set(request.uri());
                    receivedHeader.set(request.requestHeaders().get("X-Test"));
                    receivedToken.set(request.requestHeaders().get("Token"));
                    receivedCookie.set(request.requestHeaders().get("Cookie"));
                    receivedAuthorization.set(request.requestHeaders().get("Authorization"));
                    return response.header("Content-Type", "application/json").send(request.receive().retain());
                })
                .get("/redirect", (request, response) -> response.status(302).header("Location", "/destination").send())
                .get("/destination", (request, response) -> {
                    redirected.incrementAndGet();
                    return response.sendString(Mono.just("unexpected"));
                })
                .get("/slow", (request, response) -> response.sendString(Mono.delay(Duration.ofSeconds(5)).map(value -> "late")))
                .get("/large", (request, response) -> response.header("Content-Type", "text/plain")
                        .sendString(Mono.just("x".repeat(524288)))))
                .bindNow();
        HttpLabConfiguration configuration = new HttpLabConfiguration();
        connections = configuration.httpLabConnections();
        service = new HttpRequestExecutionServiceImpl(configuration.httpLabWebClient(connections));
    }

    @AfterEach
    void tearDown() {
        connections.disposeLater().block(Duration.ofSeconds(3));
        server.disposeNow();
        UserThreadLocalUtil.clear();
    }

    @Test
    void sendsExactBodyAndEnabledHeadersWithoutDoubleEncodingExistingQuery() {
        UserThreadLocalUtil.set(UserInfo.builder().userId("1").build());
        var request = new HttpRequestSpec().setMethod("POST").setUrl(url("/echo?existing=a%2Fb"))
                .setBody("{\"message\":\"你好\"}")
                .setQuery(List.of(new HttpRequestField().setName("q").setValue("a&b 中文"),
                        new HttpRequestField().setName("q").setValue("second")))
                .setHeaders(List.of(new HttpRequestField().setName("Content-Type").setValue("application/json"),
                        new HttpRequestField().setName("X-Test").setValue("sent"),
                        new HttpRequestField().setName("X-Test").setValue("ignored").setEnabled(false)));
        var result = service.execute(request).block(Duration.ofSeconds(3));

        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(request.getBody());
        assertThat(result.getResponseBytes()).isEqualTo(request.getBody().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertThat(receivedHeader.get()).isEqualTo("sent");
        assertThat(receivedToken.get()).isNull();
        assertThat(receivedCookie.get()).isNull();
        assertThat(receivedAuthorization.get()).isNull();
        assertThat(receivedUri.get()).isEqualTo("/echo?existing=a%2Fb&q=a%26b%20%E4%B8%AD%E6%96%87&q=second");
    }

    @Test
    void preservesRedirectAsTheActualResponseWithoutSendingAnotherRequest() {
        var result = service.execute(new HttpRequestSpec().setUrl(url("/redirect"))).block(Duration.ofSeconds(3));
        assertThat(result.getStatusCode()).isEqualTo(302);
        assertThat(result.getError()).isEmpty();
        assertThat(redirected).hasValue(0);
    }

    @Test
    void timesOutTheWholeExchangeAndReturnsMeasuredFailure() {
        var result = service.execute(new HttpRequestSpec().setUrl(url("/slow")).setTimeoutMs(100))
                .block(Duration.ofSeconds(3));
        assertThat(result.getError()).isEqualTo("请求总超时");
        assertThat(result.getElapsedMs()).isGreaterThanOrEqualTo(90).isLessThan(2000);
    }

    @Test
    void boundsLargeResponseAndExplicitlyMarksThePrefix() {
        var result = service.execute(new HttpRequestSpec().setUrl(url("/large"))).block(Duration.ofSeconds(3));
        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getBody()).hasSize(262144);
        assertThat(result.getResponseBytes()).isEqualTo(262145);
    }

    @Test
    void measuresARealLoadRunThroughTheSameWebClientExecutor() {
        UserThreadLocalUtil.set(UserInfo.builder().userId("test").build());
        HttpLoadTestServiceImpl loads = new HttpLoadTestServiceImpl(service);
        try {
            var run = loads.start(new HttpLoadTestDto().setRequest(new HttpRequestSpec().setUrl(url("/redirect")))
                    .setConcurrency(2).setRequestsPerSecond(5).setDurationSeconds(1));
            await().pollInSameThread().atMost(Duration.ofSeconds(3))
                    .until(() -> loads.get(run.getId()).getStatus() == HttpLoadStatus.COMPLETED);
            var result = loads.get(run.getId());
            assertThat(result.getCompleted()).isBetween(1, 5);
            assertThat(result.getStatusCounts()).containsEntry("302", result.getCompleted());
            assertThat(result.getErrorRate()).isEqualTo(100);
            assertThat(result.getAverageMs()).isPositive();
            assertThat(redirected).hasValue(0);
        } finally {
            loads.close();
        }
    }

    @Test
    void rejectsUnsupportedSchemesBeforeSendingAnyNetworkRequest() {
        assertThatThrownBy(() -> service.execute(new HttpRequestSpec().setUrl("file:///etc/hosts"))
                .block(Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(XException.class, error -> assertThat(error.getResponseCodeEnum())
                        .isEqualTo(ResponseCodeEnum.HTTP_LAB_REQUEST_INVALID));
    }

    @Test
    void releasesRequestCapacityWhenAnInFlightRequestIsCancelled() {
        HttpRequestExecutionServiceImpl bounded = new HttpRequestExecutionServiceImpl(WebClient.builder()
                .exchangeFunction(request -> Mono.never()).build());
        HttpRequestSpec request = new HttpRequestSpec().setUrl("https://example.test");
        List<Disposable> active = new ArrayList<>();
        try {
            for (int index = 0; index < 96; index++) {
                active.add(bounded.execute(request).subscribe());
            }
            assertThatThrownBy(() -> bounded.execute(request).block(Duration.ofSeconds(1)))
                    .isInstanceOfSatisfying(XException.class, error -> assertThat(error.getResponseCodeEnum())
                            .isEqualTo(ResponseCodeEnum.HTTP_LAB_BUSY));

            active.getFirst().dispose();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            active.add(bounded.execute(request).subscribe(result -> {}, failure::set));
            assertThat(failure.get()).isNull();
        } finally {
            active.forEach(Disposable::dispose);
        }
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.port() + path;
    }
}
