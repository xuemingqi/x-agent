package com.x.ai.service;

import com.x.ai.common.dto.AgentScheduleToolExecutionDto;
import com.x.ai.common.dto.AgentScheduleToolOperationDto;
import com.x.ai.common.dto.AgentScheduleToolCreateDto;
import com.x.ai.common.dto.AgentScheduleToolDetailDto;
import com.x.ai.common.dto.AgentScheduleToolSummaryDto;
import com.x.ai.common.dto.AgentScheduleToolUpdateDto;
import com.x.framework.common.response.BasePageResult;

import java.util.List;

public interface AgentScheduleToolService {

    /**
     * 为当前用户创建定时任务。
     *
     * @param userId 用户 ID
     * @param modelId 模型 ID
     * @param request 创建参数
     * @return 已创建的定时任务
     */
    AgentScheduleToolDetailDto create(String userId, Long modelId, AgentScheduleToolCreateDto request);

    /**
     * 查询当前用户的全部定时任务。
     *
     * @param userId 用户 ID
     * @return 定时任务列表
     */
    List<AgentScheduleToolSummaryDto> list(String userId);

    /**
     * 查询当前用户拥有的指定定时任务。
     *
     * @param userId 用户 ID
     * @param id 定时任务主键
     * @return 定时任务详情
     */
    AgentScheduleToolDetailDto get(String userId, Long id);

    /**
     * 更新当前用户拥有的定时任务。
     *
     * @param userId 用户 ID
     * @param modelId 模型 ID
     * @param id 定时任务主键
     * @param request 更新参数
     * @return 更新后的定时任务
     */
    AgentScheduleToolDetailDto update(String userId, Long modelId, Long id, AgentScheduleToolUpdateDto request);

    /**
     * 暂停当前用户拥有的定时任务。
     *
     * @param userId 用户 ID
     * @param id 定时任务主键
     * @return 暂停操作结果
     */
    AgentScheduleToolOperationDto pause(String userId, Long id);

    /**
     * 恢复当前用户拥有的定时任务。
     *
     * @param userId 用户 ID
     * @param id 定时任务主键
     * @return 恢复操作结果
     */
    AgentScheduleToolOperationDto resume(String userId, Long id);

    /**
     * 删除当前用户拥有的定时任务。
     *
     * @param userId 用户 ID
     * @param id 定时任务主键
     * @return 删除操作结果
     */
    AgentScheduleToolOperationDto delete(String userId, Long id);

    /**
     * 异步触发当前用户拥有的定时任务。
     *
     * @param userId 用户 ID
     * @param id 定时任务主键
     * @return 已提交的执行记录
     */
    AgentScheduleToolExecutionDto runNow(String userId, Long id);

    /**
     * 分页查询本人定时任务的执行记录与发布文件，所有标识转换为工具可无损传递的字符串。
     *
     * @param userId   运行时用户 ID
     * @param id       定时任务 ID
     * @param page     页码，从 1 开始，省略默认 1
     * @param pageSize 每页记录数，1-200，省略默认 20
     * @return 原分页信息及按创建时间倒序的执行记录
     */
    BasePageResult<List<AgentScheduleToolExecutionDto>> executions(String userId, Long id, Integer page, Integer pageSize);
}
