package com.x.ai.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AgentAppActivateDto {

    /**
     * 切换前读取到的当前版本，用于阻止并发覆盖。
     */
    @NotBlank
    @Size(max = 64)
    private String baseVersion;
}
