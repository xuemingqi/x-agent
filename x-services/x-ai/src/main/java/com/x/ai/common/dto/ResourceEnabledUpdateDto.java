package com.x.ai.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "ResourceEnabledUpdateDto", description = "修改当前用户的扩展启用状态")
public class ResourceEnabledUpdateDto {

    @NotNull(message = "启用状态不能为空")
    @Schema(description = "是否为当前用户启用")
    private Boolean enabled;
}
