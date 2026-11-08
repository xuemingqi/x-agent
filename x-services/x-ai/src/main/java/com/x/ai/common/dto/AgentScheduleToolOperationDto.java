package com.x.ai.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(description = "供工具返回的定时任务操作结果")
public class AgentScheduleToolOperationDto {

    @Schema(description = "定时任务 ID 字符串")
    private String id;

    @Schema(description = "任务状态：1-启用，2-暂停")
    private Integer status;

    private String message;
}
