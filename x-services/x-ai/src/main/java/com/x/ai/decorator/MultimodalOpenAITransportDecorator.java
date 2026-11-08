package com.x.ai.decorator;

import com.x.framework.common.utils.JsonUtil;
import io.agentscope.core.model.transport.HttpRequest;
import io.agentscope.core.model.transport.HttpResponse;
import io.agentscope.core.model.transport.HttpTransport;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@RequiredArgsConstructor
public class MultimodalOpenAITransportDecorator implements HttpTransport {

    private final HttpTransport delegate;


    @Override
    public HttpResponse execute(HttpRequest request) {
        HttpResponse response = delegate.execute(request);
        return HttpResponse.builder()
                .statusCode(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.isSuccessful() ? normalizeImages(response.getBody()) : response.getBody())
                .build();
    }

    @Override
    public Flux<String> stream(HttpRequest request) {
        return delegate.stream(request).map(this::normalizeImages);
    }

    @Override
    public void close() {

    }

    /**
     * 只处理包含 images 的 JSON，保留普通响应、错误响应及 SSE 结束标记。
     */
    private String normalizeImages(String body) {
        if (body == null || !body.contains("\"images\"")) {
            return body;
        }
        JsonNode response = JsonUtil.OBJECT_MAPPER.readTree(body);
        boolean changed = false;
        for (JsonNode choice : response.path("choices")) {
            changed |= mergeImages(choice.path("message"));
            changed |= mergeImages(choice.path("delta"));
        }
        return changed ? JsonUtil.toJsonStrOrThrow(response) : body;
    }

    /**
     * 保留文字和已有内容块，追加完整的图片块。
     */
    private boolean mergeImages(JsonNode node) {
        if (!(node instanceof ObjectNode message) || !message.path("images").isArray()) {
            return false;
        }
        ArrayNode content = JsonUtil.OBJECT_MAPPER.createArrayNode();
        JsonNode original = message.path("content");
        if (original.isArray()) {
            original.forEach(content::add);
        } else if (original.isTextual() && !original.asText().isEmpty()) {
            content.addObject().put("type", "text").put("text", original.asText());
        }
        message.path("images").forEach(content::add);
        message.set("content", content);
        message.remove("images");
        return true;
    }
}
