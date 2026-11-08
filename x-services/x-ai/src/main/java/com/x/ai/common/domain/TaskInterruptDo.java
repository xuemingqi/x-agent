package com.x.ai.common.domain;

import com.x.ai.common.enums.InterruptDispatch;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "TaskInterruptDo", description = "Agent 任务中断结果")
public class TaskInterruptDo {

    @Schema(name = "taskId", description = "任务 ID")
    private String taskId;

    @Schema(name = "dispatch", description = "中断指令分发方式")
    private InterruptDispatch dispatch;

    @Schema(name = "accepted", description = "是否已受理中断请求")
    private boolean accepted;
}
