package com.x.ai.common.domain;

import com.x.ai.db.enums.AgentScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@Accessors(chain = true)
@Schema(name = "AgentScheduleOperationDo", description = "Agent 定时任务操作结果")
public class AgentScheduleOperationDo {

    @Schema(name = "id", description = "定时任务 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(name = "status", description = "任务状态")
    private AgentScheduleStatus status;

    @Schema(name = "message", description = "操作结果说明")
    private String message;
}
