package com.x.ai.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "knowledge.embedding")
public class KnowledgeEmbeddingProperties {

    /**
     * Ollama 原生 API 服务地址。
     */
    @NotBlank
    private String baseUrl;

    /**
     * 文档与检索统一使用的向量模型名称。
     */
    @NotBlank
    private String model;

    /**
     * 预期模型向量维度，不接受静默变化。
     */
    @NotNull
    @Min(1)
    private Integer dimensions;

    /**
     * 每次 Ollama 请求的切片数量。
     */
    @NotNull
    @Min(1)
    @Max(128)
    private Integer batchSize;

    /**
     * 单次向量化请求超时，单位为秒。
     */
    @NotNull
    @Min(1)
    private Integer timeoutSeconds;
}
