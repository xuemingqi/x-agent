package com.x.api.ai.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * @author : xuemingqi
 * @since : 2026-06-27 14:30
 */
@Data
@Accessors(chain = true)
@Schema(name = "AiModelDo", description = "AI 模型配置")
public class AiModelDo {

    @Schema(name = "id", description = "模型配置ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(name = "name", description = "名称")
    private String name;

    @Schema(name = "modelUrl", description = "模型地址")
    private String modelUrl;

    @Schema(name = "modelName", description = "模型名称")
    private String modelName;

    @Schema(name = "contextWindowSize", description = "模型上下文窗口大小，单位 token")
    private Integer contextWindowSize;

    /**
     * 当前用户的模型能力编码，0 纯文本、1 图片输入、2 图片输出、3 图片输入及输出。
     */
    @Schema(description = "模型多模态能力编码")
    private Integer multimodalCapability;

    @Schema(name = "temperature", description = "模型温度")
    private Double temperature;

    @Schema(name = "isDefault", description = "是否为外部调用使用的默认模型")
    private Boolean isDefault;

    @Schema(name = "createTime", description = "创建时间")
    private LocalDateTime createTime;

    @Schema(name = "updateTime", description = "更新时间")
    private LocalDateTime updateTime;
}
