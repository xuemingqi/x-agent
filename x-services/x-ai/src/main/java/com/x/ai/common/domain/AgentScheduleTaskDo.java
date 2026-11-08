package com.x.ai.common.domain;

import com.x.ai.db.enums.AgentScheduleExecutionStatus;
import com.x.ai.db.enums.AgentScheduleMode;
import com.x.ai.db.enums.AgentScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@Schema(name = "AgentScheduleTaskDo", description = "Agent 定时任务")
public class AgentScheduleTaskDo {

    @Schema(name = "id", description = "定时任务 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(name = "taskName", description = "任务名称")
    private String taskName;

    @Schema(name = "userId", description = "任务所属用户 ID")
    private String userId;

    @Schema(name = "modelId", description = "模型配置 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    @Schema(name = "prompt", description = "任务提示词")
    private String prompt;

    @Schema(name = "scheduleMode", description = "调度模式")
    private AgentScheduleMode scheduleMode;

    @Schema(name = "cronExpression", description = "Cron 表达式")
    private String cronExpression;

    @Schema(name = "fixedRateMs", description = "固定频率间隔，单位毫秒")
    private Long fixedRateMs;

    @Schema(name = "fixedDelayMs", description = "固定延迟间隔，单位毫秒")
    private Long fixedDelayMs;

    @Schema(name = "initialDelayMs", description = "首次执行延迟，单位毫秒")
    private Long initialDelayMs;

    @Schema(name = "status", description = "任务状态")
    private AgentScheduleStatus status;

    @Schema(name = "lastRunTime", description = "最近执行时间")
    private LocalDateTime lastRunTime;

    @Schema(name = "nextRunTime", description = "下次执行时间")
    private LocalDateTime nextRunTime;

    @Schema(name = "lastExecutionStatus", description = "最近一次执行状态")
    private AgentScheduleExecutionStatus lastExecutionStatus;

    @Schema(name = "createTime", description = "创建时间")
    private LocalDateTime createTime;

    @Schema(name = "updateTime", description = "更新时间")
    private LocalDateTime updateTime;
}
