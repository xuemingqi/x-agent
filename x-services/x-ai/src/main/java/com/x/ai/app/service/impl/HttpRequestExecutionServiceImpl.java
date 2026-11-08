package com.x.ai.app.service.impl;

import com.x.ai.app.service.HttpRequestExecutionService;
import com.x.ai.common.domain.HttpExecutionDo;
import com.x.ai.common.model.HttpRequestSpec;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

@Service
public class HttpRequestExecutionServiceImpl implements HttpRequestExecutionService {

    private static final int MAX_RESPONSE_BYTES = 262144;

    private final WebClient webClient;

    private final Semaphore slots = new Semaphore(96);


    public HttpRequestExecutionServiceImpl(@Qualifier("httpLabWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<HttpExecutionDo> execute(HttpRequestSpec request) {
        return Mono.defer(() -> {
            URI uri = buildUri(request);
            if (!slots.tryAcquire()) {
                return Mono.error(new XException(ResponseCodeEnum.HTTP_LAB_BUSY));
            }
            long started = System.nanoTime();
            HttpExecutionDo result = new HttpExecutionDo().setUrl(uri.toString());
            return Mono.defer(() -> exchange(request, uri, result))
                    .timeout(Duration.ofMillis(request.getTimeoutMs()))
                    .onErrorResume(error -> Mono.just(result.setError(describeFailure(error))))
                    .map(value -> value.setElapsedMs((System.nanoTime() - started) / 1_000_000.0))
                    .doFinally(signal -> slots.release());
        });
    }

    /**
     * 保留地址已有编码，只编码编辑器追加的查询参数。
     */
    private URI buildUri(HttpRequestSpec request) {
        try {
            URI uri = URI.create(request.getUrl().trim());
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null) {
                throw new XException(ResponseCodeEnum.HTTP_LAB_REQUEST_INVALID);
            }
            var builder = UriComponentsBuilder.fromUri(uri);
            request.getQuery().stream().filter(field -> field.isEnabled() && !field.getName().isBlank())
                    .forEach(field -> builder.queryParam(UriUtils.encode(field.getName(), StandardCharsets.UTF_8),
                            UriUtils.encode(field.getValue(), StandardCharsets.UTF_8)));
            return builder.build(true).toUri();
        } catch (IllegalArgumentException error) {
            throw new XException(ResponseCodeEnum.HTTP_LAB_REQUEST_INVALID);
        }
    }

    /**
     * 只发送编辑器明确配置的头和正文，不继承平台鉴权、Cookie 或自动重试。
     */
    private Mono<HttpExecutionDo> exchange(HttpRequestSpec request, URI uri, HttpExecutionDo result) {
        var outgoing = webClient.method(HttpMethod.valueOf(request.getMethod())).uri(uri);
        outgoing.headers(headers -> request.getHeaders().stream()
                .filter(field -> field.isEnabled() && !field.getName().isBlank())
                .forEach(field -> headers.add(field.getName(), field.getValue())));
        if (!Set.of("GET", "HEAD").contains(request.getMethod()) && !request.getBody().isEmpty()) {
            outgoing.bodyValue(request.getBody().getBytes(StandardCharsets.UTF_8));
        }
        return outgoing.exchangeToMono(response -> readResponse(response, result));
    }

    /**
     * 有界读取响应前缀并释放缓冲区，截断时不会继续下载完整的大响应。
     */
    private Mono<HttpExecutionDo> readResponse(ClientResponse response, HttpExecutionDo result) {
        result.setStatusCode(response.statusCode().value());
        response.headers().asHttpHeaders().forEach((name, values) -> result.getHeaders().put(name, values));
        MediaType contentType = response.headers().contentType().orElse(null);
        var content = DataBufferUtils.takeUntilByteCount(response.body(BodyExtractors.toDataBuffers()), MAX_RESPONSE_BYTES + 1L);
        return DataBufferUtils.join(content, MAX_RESPONSE_BYTES + 1)
                .map(buffer -> decodeResponse(buffer, contentType, result))
                .defaultIfEmpty(result);
    }

    /**
     * 文本按响应声明的字符集解码；二进制结果仅显示摘要。
     */
    private HttpExecutionDo decodeResponse(DataBuffer buffer, MediaType contentType, HttpExecutionDo result) {
        try {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            result.setResponseBytes(bytes.length).setTruncated(bytes.length > MAX_RESPONSE_BYTES);
            boolean text = contentType == null || "text".equals(contentType.getType())
                    || contentType.getSubtype().contains("json") || contentType.getSubtype().contains("xml")
                    || contentType.getSubtype().contains("javascript");
            var charset = contentType != null && contentType.getCharset() != null
                    ? contentType.getCharset() : StandardCharsets.UTF_8;
            return result.setBody(text ? new String(Arrays.copyOf(bytes, Math.min(bytes.length, MAX_RESPONSE_BYTES)), charset)
                    : "[二进制响应，未显示正文]");
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    /**
     * 保留可排查的失败类型，避免将可能带目标凭证的底层异常消息返回页面。
     */
    private String describeFailure(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        if (cause instanceof XException agentError) {
            return agentError.getResponseCodeEnum().getMsg();
        }
        return cause instanceof TimeoutException ? "请求总超时" : "请求失败：" + cause.getClass().getSimpleName();
    }
}
