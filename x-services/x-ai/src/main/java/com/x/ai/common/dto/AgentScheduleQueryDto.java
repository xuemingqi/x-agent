package com.x.ai.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "AgentScheduleQueryDto", description = "查询 Agent 定时任务")
public class AgentScheduleQueryDto {

    @Min(value = 1, message = "页码必须大于 0")
    @Schema(name = "page", description = "页码", defaultValue = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数必须大于 0")
    @Max(value = 200, message = "每页条数不能超过 200")
    @Schema(name = "pageSize", description = "每页条数", defaultValue = "20")
    private Integer pageSize = 20;

    @Schema(name = "taskName", description = "任务名称模糊筛选")
    private String taskName;

    @Min(value = 1, message = "调度模式编码无效")
    @Max(value = 3, message = "调度模式编码无效")
    @Schema(name = "scheduleMode", description = "调度模式筛选：1-Cron 表达式，2-固定频率，3-固定延迟")
    private Integer scheduleMode;

    @Min(value = 1, message = "任务状态编码无效")
    @Max(value = 2, message = "任务状态编码无效")
    @Schema(name = "status", description = "任务状态筛选：1-启用，2-已暂停")
    private Integer status;
}
