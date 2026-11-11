package com.x.ai.common.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Ollama 原生向量响应。
 */
@Data
@Accessors(chain = true)
public class OllamaEmbeddingResponse {

    /**
     * 实际模型名称。
     */
    private String model;

    /**
     * 按输入顺序返回的向量。
     */
    private java.util.List<java.util.List<Double>> embeddings;
}
