package com.x.ai.common.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Ollama 原生向量请求。
 */
@Data
@Accessors(chain = true)
public class OllamaEmbeddingRequest {

    /**
     * Ollama 模型名称。
     */
    private String model;

    /**
     * 批量待向量化文本。
     */
    private java.util.List<String> input;

    /**
     * 是否允许截断，知识库始终关闭以防静默丢失内容。
     */
    private boolean truncate;
}
