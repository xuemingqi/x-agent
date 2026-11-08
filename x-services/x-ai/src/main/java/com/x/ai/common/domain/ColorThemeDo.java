package com.x.ai.common.domain;

import com.x.ai.common.model.ColorPalette;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonClassDescription("可直接用于界面预览的配色方案")
@Schema(description = "AI 配色方案")
public class ColorThemeDo {

    /**
     * 方案名称。
     */
    @NotBlank
    @Size(max = 40)
    @JsonProperty(required = true)
    @JsonPropertyDescription("有画面感的中文方案名称，不超过 40 字")
    @Schema(description = "方案名称")
    private String name;

    /**
     * 配色说明。
     */
    @NotBlank
    @Size(max = 300)
    @JsonProperty(required = true)
    @JsonPropertyDescription("用中文简述配色与用户意境的关系，不超过 300 字，不宣称通过对比度检测")
    @Schema(description = "配色说明")
    private String description;

    /**
     * 配色。
     */
    @Valid
    @NotNull
    @JsonProperty(required = true)
    @JsonPropertyDescription("完整返回七种界面颜色，并严格保留用户锁定的颜色")
    @Schema(description = "配色")
    private ColorPalette palette;
}
