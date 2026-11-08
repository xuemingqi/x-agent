package com.x.ai.service.impl;

import com.x.ai.db.enums.AgentScheduleMode;
import com.x.ai.common.domain.AgentScheduleExecutionDo;
import com.x.ai.common.domain.AgentScheduleOperationDo;
import com.x.ai.common.domain.AgentScheduleTaskDo;
import com.x.ai.common.model.AgentScheduleCreateParam;
import com.x.ai.common.model.AgentScheduleUpdateParam;
import com.x.ai.common.dto.AgentScheduleToolCreateDto;
import com.x.ai.common.dto.AgentScheduleToolDetailDto;
import com.x.ai.common.dto.AgentScheduleToolExecutionDto;
import com.x.ai.common.dto.AgentScheduleToolOperationDto;
import com.x.ai.common.dto.AgentScheduleToolSummaryDto;
import com.x.ai.common.dto.AgentScheduleToolUpdateDto;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.response.BasePageResult;
import com.x.ai.service.AgentScheduleManagementService;
import com.x.ai.service.AgentScheduleToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AgentScheduleToolServiceImpl implements AgentScheduleToolService {

    private static final int DEFAULT_PAGE = 1;

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AgentScheduleManagementService agentScheduleManagementService;


    @Override
    public AgentScheduleToolDetailDto create(String userId, Long modelId, AgentScheduleToolCreateDto request) {
        if (request == null) {
            throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
        }
        AgentScheduleCreateParam param = new AgentScheduleCreateParam()
                .setTaskName(request.getTaskName())
                .setModelId(modelId)
                .setPrompt(request.getPrompt())
                .setScheduleMode(toScheduleMode(request.getScheduleMode()))
                .setCronExpression(request.getCronExpression())
                .setFixedRateMs(request.getFixedRateMs())
                .setFixedDelayMs(request.getFixedDelayMs())
                .setInitialDelayMs(request.getInitialDelayMs());
        AgentScheduleTaskDo task = agentScheduleManagementService.create(userId, param);
        return toDetail(task);
    }

    @Override
    public List<AgentScheduleToolSummaryDto> list(String userId) {
        return agentScheduleManagementService.list(userId).stream().map(this::toSummary).toList();
    }

    @Override
    public AgentScheduleToolDetailDto get(String userId, Long id) {
        return toDetail(agentScheduleManagementService.get(userId, id));
    }

    @Override
    public AgentScheduleToolDetailDto update(String userId, Long modelId, Long id, AgentScheduleToolUpdateDto request) {
        if (request == null) {
            throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
        }
        AgentScheduleUpdateParam param = new AgentScheduleUpdateParam()
                .setTaskName(request.getTaskName())
                .setModelId(modelId)
                .setPrompt(request.getPrompt())
                .setScheduleMode(toScheduleMode(request.getScheduleMode()))
                .setCronExpression(request.getCronExpression())
                .setFixedRateMs(request.getFixedRateMs())
                .setFixedDelayMs(request.getFixedDelayMs())
                .setInitialDelayMs(request.getInitialDelayMs());
        AgentScheduleTaskDo task = agentScheduleManagementService.update(userId, id, param);
        return toDetail(task);
    }

    @Override
    public AgentScheduleToolOperationDto pause(String userId, Long id) {
        return toOperation(agentScheduleManagementService.pause(userId, id));
    }

    @Override
    public AgentScheduleToolOperationDto resume(String userId, Long id) {
        return toOperation(agentScheduleManagementService.resume(userId, id));
    }

    @Override
    public AgentScheduleToolOperationDto delete(String userId, Long id) {
        return toOperation(agentScheduleManagementService.delete(userId, id));
    }

    @Override
    public AgentScheduleToolExecutionDto runNow(String userId, Long id) {
        return toExecution(agentScheduleManagementService.runNow(userId, id));
    }

    @Override
    public BasePageResult<List<AgentScheduleToolExecutionDto>> executions(String userId, Long id, Integer page, Integer pageSize) {
        BasePageResult<List<AgentScheduleExecutionDo>> source = agentScheduleManagementService.executions(userId, id,
                page == null ? DEFAULT_PAGE : page, pageSize == null ? DEFAULT_PAGE_SIZE : pageSize);
        BasePageResult<List<AgentScheduleToolExecutionDto>> result = new BasePageResult<>();
        result.setPageData(source.getPageData());
        result.setResults(source.getResults().stream().map(this::toExecution).toList());
        return result;
    }

    /**
     * Tool 使用整数编码，进入业务层前转换并拒绝未定义的调度模式。
     */
    private AgentScheduleMode toScheduleMode(int code) {
        AgentScheduleMode mode = AgentScheduleMode.fromCode(code);
        if (mode == null) {
            throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
        }
        return mode;
    }

    /**
     * 任务定位 ID 在模型和工具之间始终保持字符串。
     */
    private AgentScheduleToolSummaryDto toSummary(AgentScheduleTaskDo task) {
        return new AgentScheduleToolSummaryDto()
                .setId(Objects.toString(task.getId(), null))
                .setTaskName(task.getTaskName())
                .setScheduleMode(task.getScheduleMode())
                .setStatus(task.getStatus())
                .setNextRunTime(task.getNextRunTime());
    }

    /**
     * 返回修改所需配置，并避免依赖 HTTP 专用的 Long 字符串序列化注解。
     */
    private AgentScheduleToolDetailDto toDetail(AgentScheduleTaskDo task) {
        return new AgentScheduleToolDetailDto()
                .setId(Objects.toString(task.getId(), null))
                .setTaskName(task.getTaskName())
                .setPrompt(task.getPrompt())
                .setScheduleMode(task.getScheduleMode())
                .setCronExpression(task.getCronExpression())
                .setFixedRateMs(task.getFixedRateMs())
                .setFixedDelayMs(task.getFixedDelayMs())
                .setInitialDelayMs(task.getInitialDelayMs())
                .setStatus(task.getStatus())
                .setNextRunTime(task.getNextRunTime());
    }

    /**
     * 后台执行记录中的 ID 和时间统一转为字符串，状态明确返回整数编码。
     */
    private AgentScheduleToolExecutionDto toExecution(AgentScheduleExecutionDo execution) {
        return new AgentScheduleToolExecutionDto()
                .setId(Objects.toString(execution.getId(), null))
                .setScheduleTaskId(Objects.toString(execution.getScheduleTaskId(), null))
                .setTriggerSource(execution.getTriggerSource() == null ? null : execution.getTriggerSource().getCode())
                .setScheduledFireTime(Objects.toString(execution.getScheduledFireTime(), null))
                .setTaskId(execution.getTaskId())
                .setModelId(Objects.toString(execution.getModelId(), null))
                .setStatus(execution.getStatus() == null ? null : execution.getStatus().getCode())
                .setAttemptCount(execution.getAttemptCount())
                .setStartTime(Objects.toString(execution.getStartTime(), null))
                .setFinishTime(Objects.toString(execution.getFinishTime(), null))
                .setResultSummary(execution.getResultSummary())
                .setErrorSummary(execution.getErrorSummary())
                .setFiles(execution.getFiles())
                .setCreateTime(Objects.toString(execution.getCreateTime(), null));
    }

    /**
     * 暂停、恢复及删除的后续定位也不能把雪花 ID 暴露为 JSON 数字。
     */
    private AgentScheduleToolOperationDto toOperation(AgentScheduleOperationDo operation) {
        return new AgentScheduleToolOperationDto()
                .setId(Objects.toString(operation.getId(), null))
                .setStatus(operation.getStatus() == null ? null : operation.getStatus().getCode())
                .setMessage(operation.getMessage());
    }
}
