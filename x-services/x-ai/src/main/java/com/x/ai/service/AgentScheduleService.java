package com.x.ai.service;

import com.x.ai.common.domain.AgentScheduleExecutionDo;
import com.x.ai.common.domain.AgentScheduleOperationDo;
import com.x.ai.common.domain.AgentScheduleTaskDo;
import com.x.ai.common.dto.AgentScheduleCreateDto;
import com.x.ai.common.dto.AgentScheduleQueryDto;
import com.x.ai.common.dto.AgentScheduleUpdateDto;
import com.x.framework.common.response.BasePageResult;

import java.util.List;

public interface AgentScheduleService {

    /**
     * 为当前登录用户创建定时任务。
     *
     * @param request 创建参数
     * @return 已创建的定时任务
     */
    AgentScheduleTaskDo create(AgentScheduleCreateDto request);

    /**
     * 分页查询当前登录用户的定时任务。
     *
     * @param request 查询参数
     * @return 定时任务分页结果
     */
    BasePageResult<List<AgentScheduleTaskDo>> page(AgentScheduleQueryDto request);

    /**
     * 查询当前登录用户拥有的定时任务。
     *
     * @param id 定时任务主键
     * @return 定时任务详情
     */
    AgentScheduleTaskDo get(Long id);

    /**
     * 更新当前登录用户拥有的定时任务。
     *
     * @param id 定时任务主键
     * @param request 更新参数
     * @return 更新后的定时任务
     */
    AgentScheduleTaskDo update(Long id, AgentScheduleUpdateDto request);

    /**
     * 暂停当前登录用户拥有的定时任务。
     *
     * @param id 定时任务主键
     * @return 暂停操作结果
     */
    AgentScheduleOperationDo pause(Long id);

    /**
     * 恢复当前登录用户拥有的定时任务。
     *
     * @param id 定时任务主键
     * @return 恢复操作结果
     */
    AgentScheduleOperationDo resume(Long id);

    /**
     * 删除当前登录用户拥有的定时任务。
     *
     * @param id 定时任务主键
     * @return 删除操作结果
     */
    AgentScheduleOperationDo delete(Long id);

    /**
     * 立即触发当前登录用户拥有的定时任务。
     *
     * @param id 定时任务主键
     * @return 已提交的执行记录
     */
    AgentScheduleExecutionDo runNow(Long id);

    /**
     * 分页查询当前登录用户拥有的定时任务执行记录。
     *
     * @param id 定时任务主键
     * @param page 页码
     * @param pageSize 每页条数
     * @return 执行记录分页结果
     */
    BasePageResult<List<AgentScheduleExecutionDo>> executions(Long id, int page, int pageSize);
}
