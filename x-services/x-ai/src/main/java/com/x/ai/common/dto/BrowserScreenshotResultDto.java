package com.x.ai.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "BrowserScreenshotResultDto", description = "用户授权的截图回传结果")
public class BrowserScreenshotResultDto {

    @NotNull
    @Schema(name = "accepted", description = "是否同意并完成截图；取消选择或拒绝时为 false")
    private Boolean accepted;

    @Schema(name = "fileId", description = "同意时必填，当前会话中已上传且尚未绑定的 PNG 图片附件 ID")
    private Long fileId;
}
