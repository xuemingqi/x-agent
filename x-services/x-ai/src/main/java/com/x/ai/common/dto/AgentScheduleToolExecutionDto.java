package com.x.ai.common.dto;

import com.x.ai.common.domain.ConversationFileDo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@Schema(description = "供工具返回的执行记录，所有 ID 均为字符串，时间为平台本地 ISO 时间")
public class AgentScheduleToolExecutionDto {

    @Schema(description = "执行记录 ID")
    private String id;

    @Schema(description = "所属定时任务 ID")
    private String scheduleTaskId;

    @Schema(description = "触发来源：1-定时，2-手动")
    private Integer triggerSource;

    private String scheduledFireTime;

    @Schema(description = "Agent 任务 ID")
    private String taskId;

    @Schema(description = "实际执行模型 ID")
    private String modelId;

    @Schema(description = "执行状态：1-已创建，2-执行中，3-成功，4-失败，5-中断")
    private Integer status;

    private Integer attemptCount;

    private String startTime;

    private String finishTime;

    @Schema(description = "结果摘要，非完整对话正文")
    private String resultSummary;

    private String errorSummary;

    @Schema(description = "本次执行已发布的文件，文件和消息 ID 也为字符串")
    private List<ConversationFileDo> files = List.of();

    private String createTime;
}
