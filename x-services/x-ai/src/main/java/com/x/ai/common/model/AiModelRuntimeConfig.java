package com.x.ai.common.model;

import com.x.ai.db.enums.AgentModelMultimodalCapability;
import com.x.framework.common.annotation.LogIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 已完成用户归属与可用性校验的模型运行配置，不作为接口响应返回。
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
public class AiModelRuntimeConfig {

    /**
     * 当前用户拥有的模型配置 ID。
     */
    private Long modelId;

    /**
     * 提供方的实际模型名称。
     */
    private String modelName;

    /**
     * OpenAI 兼容接口基础地址。
     */
    private String baseUrl;

    /**
     * 仅用于当前用户模型请求的密钥，不允许记录到日志。
     */
    @ToString.Exclude
    @LogIgnore
    private String apiKey;

    /**
     * 提供方实际支持的上下文 token 数。
     */
    private Integer contextWindowSize;

    /**
     * 生成温度，为空时使用提供方默认值。
     */
    private Double temperature;

    /**
     * 用户为该模型配置的图片输入和输出能力。
     */
    private AgentModelMultimodalCapability multimodalCapability;
}
