package com.x.ai.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterruptCommand {

    /**
     * 待中断任务 ID。
     */
    private String taskId;

    /**
     * 任务所属用户 ID。
     */
    private String userId;
}
