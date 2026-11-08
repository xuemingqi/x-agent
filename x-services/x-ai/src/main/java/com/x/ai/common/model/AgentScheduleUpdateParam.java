package com.x.ai.common.model;

import com.x.ai.db.enums.AgentScheduleMode;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 管理服务层更新定时任务的内部参数，与 Controller 和 Tool 层的 DTO 完全解耦。
 */
@Data
@Accessors(chain = true)
public class AgentScheduleUpdateParam {

    /**
     * 任务名称。
     */
    private String taskName;

    /**
     * 模型配置 ID。
     */
    private Long modelId;

    /**
     * 任务提示词。
     */
    private String prompt;

    /**
     * 调度模式。
     */
    private AgentScheduleMode scheduleMode;

    /**
     * Cron 表达式，仅 CRON 模式使用。
     */
    private String cronExpression;

    /**
     * 固定频率间隔，单位毫秒。
     */
    private Long fixedRateMs;

    /**
     * 固定延迟间隔，单位毫秒。
     */
    private Long fixedDelayMs;

    /**
     * 首次执行延迟，单位毫秒。
     */
    private Long initialDelayMs;
}