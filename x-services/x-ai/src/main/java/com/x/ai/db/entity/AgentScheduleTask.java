package com.x.ai.db.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.x.ai.db.enums.AgentScheduleExecutionStatus;
import com.x.ai.db.enums.AgentScheduleMode;
import com.x.ai.db.enums.AgentScheduleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Builder(toBuilder = true)
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_schedule_task")
public class AgentScheduleTask {

    /**
     * 定时任务主键。
     */
    @TableId("id")
    private Long id;

    /**
     * 任务名称。
     */
    @TableField("task_name")
    private String taskName;

    /**
     * 任务所属用户 ID。
     */
    @TableField("user_id")
    private String userId;

    /**
     * 用户选择的模型配置 ID。
     */
    @TableField("model_id")
    private Long modelId;

    /**
     * 定时执行的任务提示词。
     */
    @TableField("prompt")
    private String prompt;

    /**
     * 调度模式。
     */
    @TableField("schedule_mode")
    private AgentScheduleMode scheduleMode;

    /**
     * Cron 表达式。
     */
    @TableField("cron_expression")
    private String cronExpression;

    /**
     * 固定频率间隔，单位毫秒。
     */
    @TableField("fixed_rate_ms")
    private Long fixedRateMs;

    /**
     * 固定延迟间隔，单位毫秒。
     */
    @TableField("fixed_delay_ms")
    private Long fixedDelayMs;

    /**
     * 首次执行延迟，单位毫秒。
     */
    @TableField("initial_delay_ms")
    private Long initialDelayMs;

    /**
     * 任务业务状态。
     */
    @TableField("status")
    private AgentScheduleStatus status;

    /**
     * 最近一次执行时间。
     */
    @TableField("last_run_time")
    private LocalDateTime lastRunTime;

    /**
     * 最近一次执行状态。
     */
    @TableField("last_execution_status")
    private AgentScheduleExecutionStatus lastExecutionStatus;

    /**
     * 创建时间。
     */
    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
