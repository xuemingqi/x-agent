package com.x.ai.common.dto;

import io.agentscope.core.tool.ToolParam;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AgentScheduleToolUpdateDto {

    @ToolParam(name = "taskName", description = "任务名称")
    private String taskName;

    @ToolParam(name = "prompt", description = "替换原配置的完整任务提示词，必须保留仍有效的工作要求")
    private String prompt;

    @ToolParam(name = "scheduleMode", description = "调度模式，只能传整数：1-Cron 表达式，2-固定频率，3-固定延迟")
    private int scheduleMode;

    @ToolParam(name = "cronExpression", description = "Cron 表达式，仅当 scheduleMode=1 时传入", required = false)
    private String cronExpression;

    @ToolParam(name = "fixedRateMs", description = "固定频率间隔，单位毫秒，仅当 scheduleMode=2 时传入", required = false)
    private Long fixedRateMs;

    @ToolParam(name = "fixedDelayMs", description = "固定延迟间隔，单位毫秒，仅当 scheduleMode=3 时传入", required = false)
    private Long fixedDelayMs;

    @ToolParam(name = "initialDelayMs", description = "首次执行延迟，单位毫秒，不传则不延迟", required = false)
    private Long initialDelayMs;
}
