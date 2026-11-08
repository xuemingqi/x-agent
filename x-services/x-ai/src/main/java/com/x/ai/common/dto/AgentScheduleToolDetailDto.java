package com.x.ai.common.dto;

import com.x.ai.db.enums.AgentScheduleMode;
import com.x.ai.db.enums.AgentScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@Schema(name = "AgentScheduleToolDetailDto", description = "供 Agent Tool 使用的定时任务必要配置")
public class AgentScheduleToolDetailDto {

    @Schema(name = "id", description = "任务 ID")
    private String id;

    @Schema(name = "taskName", description = "任务名称")
    private String taskName;

    @Schema(name = "prompt", description = "定时执行的任务提示词")
    private String prompt;

    @Schema(name = "scheduleMode", description = "调度模式")
    private AgentScheduleMode scheduleMode;

    @Schema(name = "cronExpression", description = "Cron 表达式，仅 CRON 模式使用")
    private String cronExpression;

    @Schema(name = "fixedRateMs", description = "固定频率间隔，单位毫秒")
    private Long fixedRateMs;

    @Schema(name = "fixedDelayMs", description = "固定延迟间隔，单位毫秒")
    private Long fixedDelayMs;

    @Schema(name = "initialDelayMs", description = "首次执行延迟，单位毫秒")
    private Long initialDelayMs;

    @Schema(name = "status", description = "任务状态")
    private AgentScheduleStatus status;

    @Schema(name = "nextRunTime", description = "下次执行时间")
    private LocalDateTime nextRunTime;
}
