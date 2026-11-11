package com.x.ai.client;

import com.x.ai.common.model.OllamaEmbeddingRequest;
import com.x.ai.common.model.OllamaEmbeddingResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Ollama 原生批量向量协议。
 */
public interface OllamaEmbeddingClient {

    @PostMapping(value = "/api/embed", consumes = "application/json")
    OllamaEmbeddingResponse embed(@RequestBody OllamaEmbeddingRequest request);
}
