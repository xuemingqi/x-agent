package com.x.ai.common.model;

import com.x.ai.db.enums.AgentScheduleMode;
import com.x.ai.db.enums.AgentScheduleStatus;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 管理服务层分页查询定时任务的内部参数，与 Controller 层的 DTO 完全解耦。
 */
@Data
@Accessors(chain = true)
public class AgentScheduleQueryParam {

    /**
     * 页码，从 1 开始。
     */
    private Integer page;

    /**
     * 每页条数。
     */
    private Integer pageSize;

    /**
     * 任务名称模糊筛选。
     */
    private String taskName;

    /**
     * 调度模式筛选。
     */
    private AgentScheduleMode scheduleMode;

    /**
     * 任务状态筛选。
     */
    private AgentScheduleStatus status;
}