package com.x.ai.common.dto;

import com.x.ai.db.enums.AgentScheduleMode;
import com.x.ai.db.enums.AgentScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@Schema(name = "AgentScheduleToolSummaryDto", description = "供 Agent Tool 使用的定时任务摘要")
public class AgentScheduleToolSummaryDto {

    @Schema(name = "id", description = "任务 ID")
    private String id;

    @Schema(name = "taskName", description = "任务名称")
    private String taskName;

    @Schema(name = "scheduleMode", description = "调度模式")
    private AgentScheduleMode scheduleMode;

    @Schema(name = "status", description = "任务状态")
    private AgentScheduleStatus status;

    @Schema(name = "nextRunTime", description = "下次执行时间")
    private LocalDateTime nextRunTime;
}
