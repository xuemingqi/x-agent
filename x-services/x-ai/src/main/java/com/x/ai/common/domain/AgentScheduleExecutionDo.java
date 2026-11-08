package com.x.ai.common.domain;

import com.x.ai.db.enums.AgentScheduleExecutionStatus;
import com.x.ai.db.enums.AgentScheduleTriggerSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
@Schema(name = "AgentScheduleExecutionDo", description = "Agent 定时任务执行记录")
public class AgentScheduleExecutionDo {

    @Schema(name = "id", description = "执行记录 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(name = "scheduleTaskId", description = "定时任务 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long scheduleTaskId;

    @Schema(name = "triggerSource", description = "触发来源")
    private AgentScheduleTriggerSource triggerSource;

    @Schema(name = "scheduledFireTime", description = "计划触发时间")
    private LocalDateTime scheduledFireTime;

    @Schema(name = "taskId", description = "Agent 任务 ID")
    private String taskId;

    @Schema(name = "ownerUserId", description = "任务所属用户 ID")
    private String ownerUserId;

    @Schema(name = "modelId", description = "实际使用的模型配置 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    @Schema(name = "status", description = "执行状态")
    private AgentScheduleExecutionStatus status;

    @Schema(name = "attemptCount", description = "执行尝试次数")
    private Integer attemptCount;

    @Schema(name = "startTime", description = "开始时间")
    private LocalDateTime startTime;

    @Schema(name = "finishTime", description = "完成时间")
    private LocalDateTime finishTime;

    @Schema(name = "resultSummary", description = "执行结果摘要")
    private String resultSummary;

    @Schema(name = "errorSummary", description = "执行错误摘要")
    private String errorSummary;

    @Schema(name = "files", description = "本次执行生成的可下载文件")
    private List<ConversationFileDo> files = List.of();

    @Schema(name = "createTime", description = "创建时间")
    private LocalDateTime createTime;

    @Schema(name = "updateTime", description = "更新时间")
    private LocalDateTime updateTime;
}
