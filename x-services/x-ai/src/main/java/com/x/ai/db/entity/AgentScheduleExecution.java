package com.x.ai.db.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.x.ai.db.enums.AgentScheduleExecutionStatus;
import com.x.ai.db.enums.AgentScheduleTriggerSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_schedule_execution")
public class AgentScheduleExecution {

    /**
     * 执行记录主键。
     */
    @TableId("id")
    private Long id;

    /**
     * 关联的定时任务 ID。
     */
    @TableField("schedule_task_id")
    private Long scheduleTaskId;

    /**
     * 本次执行的触发来源。
     */
    @TableField("trigger_source")
    private AgentScheduleTriggerSource triggerSource;

    /**
     * Quartz 计划触发时间。
     */
    @TableField("scheduled_fire_time")
    private LocalDateTime scheduledFireTime;

    /**
     * Agent 执行任务 ID。
     */
    @TableField("task_id")
    private String taskId;

    /**
     * 任务所属用户 ID。
     */
    @TableField("user_id")
    private String userId;

    /**
     * 实际使用的模型配置 ID。
     */
    @TableField("model_id")
    private Long modelId;

    /**
     * 执行状态。
     */
    @TableField("status")
    private AgentScheduleExecutionStatus status;

    /**
     * 已尝试执行次数。
     */
    @TableField("attempt_count")
    private Integer attemptCount;

    /**
     * 开始执行时间。
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 完成执行时间。
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 执行结果摘要。
     */
    @TableField("result_summary")
    private String resultSummary;

    /**
     * 执行错误摘要。
     */
    @TableField("error_summary")
    private String errorSummary;

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
