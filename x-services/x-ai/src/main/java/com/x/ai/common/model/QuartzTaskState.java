package com.x.ai.common.model;

import lombok.Data;
import lombok.experimental.Accessors;
import org.quartz.Trigger;

import java.time.Instant;

@Data
@Accessors(chain = true)
public class QuartzTaskState {

    /**
     * Quartz 任务是否存在。
     */
    private boolean exists;

    /**
     * Quartz 触发器状态。
     */
    private Trigger.TriggerState triggerState;

    /**
     * 下一次计划触发时间。
     */
    private Instant nextFireTime;
}
