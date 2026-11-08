package com.x.ai.service;

import com.x.ai.common.domain.AgentScheduleExecutionDo;
import com.x.ai.common.domain.AgentScheduleOperationDo;
import com.x.ai.common.domain.AgentScheduleTaskDo;
import com.x.ai.common.model.AgentScheduleCreateParam;
import com.x.ai.common.model.AgentScheduleQueryParam;
import com.x.ai.common.model.AgentScheduleUpdateParam;
import com.x.ai.common.model.QuartzFireMetadata;
import com.x.framework.common.response.BasePageResult;

import java.util.List;

public interface AgentScheduleManagementService {

    /**
     * 为指定用户创建并注册定时任务。
     *
     * @param userId 用户 ID
     * @param param  创建参数
     * @return 已创建的定时任务
     */
    AgentScheduleTaskDo create(String userId, AgentScheduleCreateParam param);

    /**
     * 分页查询指定用户的定时任务。
     *
     * @param userId 用户 ID
     * @param param  查询参数
     * @return 定时任务分页结果
     */
    BasePageResult<List<AgentScheduleTaskDo>> page(String userId, AgentScheduleQueryParam param);

    /**
     * 查询指定用户的全部定时任务。
     *
     * @param userId 用户 ID
     * @return 定时任务列表
     */
    List<AgentScheduleTaskDo> list(String userId);

    /**
     * 查询指定用户拥有的定时任务。
     *
     * @param userId 用户 ID
     * @param id 定时任务主键
     * @return 定时任务详情
     */
    AgentScheduleTaskDo get(String userId, Long id);

    /**
     * 更新指定用户拥有的定时任务。
     *
     * @param userId 用户 ID
     * @param id     定时任务主键
     * @param param  更新参数
     * @return 更新后的定时任务
     */
    AgentScheduleTaskDo update(String userId, Long id, AgentScheduleUpdateParam param);

    /**
     * 暂停指定用户拥有的定时任务。
     *
     * @param userId 用户 ID
     * @param id 定时任务主键
     * @return 暂停操作结果
     */
    AgentScheduleOperationDo pause(String userId, Long id);

    /**
     * 恢复指定用户拥有的定时任务。
     *
     * @param userId 用户 ID
     * @param id 定时任务主键
     * @return 恢复操作结果
     */
    AgentScheduleOperationDo resume(String userId, Long id);

    /**
     * 删除指定用户拥有的定时任务。
     *
     * @param userId 用户 ID
     * @param id 定时任务主键
     * @return 删除操作结果
     */
    AgentScheduleOperationDo delete(String userId, Long id);

    /**
     * 立即触发指定用户拥有的定时任务。
     *
     * @param userId 用户 ID
     * @param id 定时任务主键
     * @return 已提交的执行记录
     */
    AgentScheduleExecutionDo runNow(String userId, Long id);

    /**
     * 分页查询指定用户拥有的定时任务执行记录。
     *
     * @param userId 用户 ID
     * @param id 定时任务主键
     * @param page 页码
     * @param pageSize 每页条数
     * @return 执行记录分页结果
     */
    BasePageResult<List<AgentScheduleExecutionDo>> executions(String userId, Long id, int page, int pageSize);

    /**
     * 执行调度器触发的任务并持久化执行状态。
     *
     * @param scheduleTaskId 定时任务主键
     * @param executionId 手动触发时预生成的执行记录 ID
     * @param metadata Quartz 触发元数据
     */
    void executeScheduled(Long scheduleTaskId, Long executionId, QuartzFireMetadata metadata);

    /**
     * 为仍处于 ACTIVE 状态的 FIXED_DELAY 任务注册下一次触发。
     *
     * @param scheduleTaskId 定时任务主键
     */
    void continueFixedDelay(Long scheduleTaskId);
}
