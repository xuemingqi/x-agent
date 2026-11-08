package com.x.ai.common.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonClassDescription("完整的界面配色，所有颜色使用六位十六进制格式 #RRGGBB")
@Schema(description = "界面配色值，可作为编辑快照和生成结果")
public class ColorPalette {

    /**
     * 页面背景色。
     */
    @NotNull
    @Pattern(regexp = "#[0-9a-fA-F]{6}")
    @JsonProperty(required = true)
    @JsonPropertyDescription("页面背景色，格式为 #RRGGBB")
    @Schema(description = "页面背景色")
    private String background;

    /**
     * 卡片和输入框背景色。
     */
    @NotNull
    @Pattern(regexp = "#[0-9a-fA-F]{6}")
    @JsonProperty(required = true)
    @JsonPropertyDescription("卡片和输入框背景色，格式为 #RRGGBB")
    @Schema(description = "卡片和输入框背景色")
    private String surface;

    /**
     * 主要按钮与强调色。
     */
    @NotNull
    @Pattern(regexp = "#[0-9a-fA-F]{6}")
    @JsonProperty(required = true)
    @JsonPropertyDescription("主要按钮与强调色，格式为 #RRGGBB")
    @Schema(description = "主要按钮与强调色")
    private String primary;

    /**
     * 主要按钮上的文字色，与 primary 对比度至少 4.5:1。
     */
    @NotNull
    @Pattern(regexp = "#[0-9a-fA-F]{6}")
    @JsonProperty(required = true)
    @JsonPropertyDescription("主要按钮上的文字色，与 primary 对比度至少 4.5:1，格式为 #RRGGBB")
    @Schema(description = "主要按钮上的文字色，与 primary 对比度至少 4.5:1")
    private String onPrimary;

    /**
     * 正文颜色，与 background、surface 对比度至少 4.5:1。
     */
    @NotNull
    @Pattern(regexp = "#[0-9a-fA-F]{6}")
    @JsonProperty(required = true)
    @JsonPropertyDescription("正文颜色，与 background、surface 对比度至少 4.5:1，格式为 #RRGGBB")
    @Schema(description = "正文颜色，与 background、surface 对比度至少 4.5:1")
    private String text;

    /**
     * 辅助文字颜色，与 background、surface 对比度至少 4.5:1。
     */
    @NotNull
    @Pattern(regexp = "#[0-9a-fA-F]{6}")
    @JsonProperty(required = true)
    @JsonPropertyDescription("辅助文字颜色，与 background、surface 对比度至少 4.5:1，格式为 #RRGGBB")
    @Schema(description = "辅助文字颜色，与 background、surface 对比度至少 4.5:1")
    private String mutedText;

    /**
     * 边框颜色，须能区分相邻区域。
     */
    @NotNull
    @Pattern(regexp = "#[0-9a-fA-F]{6}")
    @JsonProperty(required = true)
    @JsonPropertyDescription("边框颜色，须能区分相邻区域，格式为 #RRGGBB")
    @Schema(description = "边框颜色，须能区分相邻区域")
    private String border;

}
