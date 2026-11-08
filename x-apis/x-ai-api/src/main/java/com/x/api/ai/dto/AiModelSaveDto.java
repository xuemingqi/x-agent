package com.x.api.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author : xuemingqi
 * @since : 2026-06-27 14:30
 */
@Data
@Accessors(chain = true)
@Schema(name = "AiModelSaveDto", description = "AI 模型保存请求")
public class AiModelSaveDto {

    @NotBlank(message = "名称不能为空")
    @Schema(name = "name", description = "名称")
    private String name;

    @NotBlank(message = "模型地址不能为空")
    @Schema(name = "modelUrl", description = "模型地址")
    private String modelUrl;

    @NotBlank(message = "模型名称不能为空")
    @Schema(name = "modelName", description = "模型名称")
    private String modelName;

    @Schema(name = "modelKey", description = "模型 Key，新增必填；编辑为空时保留原 Key")
    private String modelKey;

    @NotNull(message = "模型上下文窗口大小不能为空")
    @Positive(message = "模型上下文窗口大小必须大于 0")
    @Schema(name = "contextWindowSize", description = "模型上下文窗口大小，单位 token")
    private Integer contextWindowSize;

    /**
     * 当前用户的模型能力编码，默认纯文本。
     */
    @NotNull(message = "模型能力不能为空")
    @Min(value = 0, message = "模型能力编码不能小于 0")
    @Max(value = 3, message = "模型能力编码不能大于 3")
    @Schema(description = "多模态能力：0 纯文本，1 图片输入，2 图片输出，3 图片输入及输出")
    private Integer multimodalCapability = 0;

    @DecimalMin(value = "0.0", message = "模型温度不能小于 0")
    @DecimalMax(value = "2.0", message = "模型温度不能大于 2")
    @Schema(name = "temperature", description = "模型温度，取值范围 0-2")
    private Double temperature;

    @Schema(name = "isDefault", description = "是否设为外部调用使用的默认模型")
    private Boolean isDefault;
}
