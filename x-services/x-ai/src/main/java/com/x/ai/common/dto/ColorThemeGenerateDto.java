package com.x.ai.common.dto;

import com.x.ai.common.model.ColorPalette;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain = true)
@Schema(description = "生成或优化配色")
public class ColorThemeGenerateDto {

    /**
     * 配色灵感，最多 1000 字。
     */
    @NotBlank
    @Size(max = 1000)
    @Schema(description = "配色灵感，最多 1000 字")
    private String prompt;

    /**
     * 模型 ID，省略时使用默认模型。
     */
    @Positive
    @Schema(description = "模型 ID，省略时使用默认模型")
    private Long modelId;

    /**
     * 深色模式。
     */
    @Schema(description = "深色模式")
    private boolean dark;

    /**
     * 鲜明配色。
     */
    @Schema(description = "鲜明配色")
    private boolean vivid;

    /**
     * 优化当前方案的可读性。
     */
    @Schema(description = "优化当前方案的可读性")
    private boolean improveReadability;

    /**
     * 当前配色快照。
     */
    @Valid
    @Schema(description = "当前配色快照")
    private ColorPalette currentPalette;

    /**
     * 锁定颜色。
     */
    @NotNull
    @Size(max = 7)
    @Schema(description = "锁定颜色")
    private Map<@NotNull @Pattern(regexp = "background|surface|primary|onPrimary|text|mutedText|border") String,
            @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String> lockedColors = Map.of();
}
