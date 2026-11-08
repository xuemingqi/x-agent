package com.x.ai.common.dto;

import com.x.ai.db.enums.AgentScheduleMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "AgentScheduleCreateDto", description = "创建 Agent 定时任务")
public class AgentScheduleCreateDto {

    @NotBlank(message = "任务名称不能为空")
    @Schema(name = "taskName", description = "任务名称")
    private String taskName;

    @NotNull(message = "模型配置 ID 不能为空")
    @Schema(name = "modelId", description = "用户选择的模型配置 ID")
    private Long modelId;

    @NotBlank(message = "任务描述不能为空")
    @Schema(name = "prompt", description = "定时执行的任务提示词")
    private String prompt;

    @NotNull(message = "调度模式不能为空")
    @Schema(name = "scheduleMode", description = "调度模式")
    private AgentScheduleMode scheduleMode;

    @Schema(name = "cronExpression", description = "Cron 表达式，仅 CRON 模式使用")
    private String cronExpression;

    @Schema(name = "fixedRateMs", description = "固定频率间隔，单位毫秒")
    private Long fixedRateMs;

    @Schema(name = "fixedDelayMs", description = "固定延迟间隔，单位毫秒")
    private Long fixedDelayMs;

    @PositiveOrZero(message = "初始延迟时间必须为非负数")
    @Schema(name = "initialDelayMs", description = "首次执行延迟，单位毫秒")
    private Long initialDelayMs;
}
