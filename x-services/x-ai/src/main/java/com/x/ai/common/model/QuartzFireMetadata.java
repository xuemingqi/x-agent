package com.x.ai.common.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Accessors(chain = true)
public class QuartzFireMetadata {

    /**
     * Quartz 计划触发时间。
     */
    private Instant scheduledFireTime;

    /**
     * Quartz 实际触发时间。
     */
    private Instant fireTime;

    /**
     * 是否为故障恢复触发。
     */
    private boolean recovering;

    /**
     * 当前重试触发次数。
     */
    private int refireCount;
}
