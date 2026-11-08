package com.x.ai.service.impl;

import com.x.framework.common.utils.UserThreadLocalUtil;

import com.x.ai.common.domain.AgentScheduleExecutionDo;
import com.x.ai.common.domain.AgentScheduleOperationDo;
import com.x.ai.common.domain.AgentScheduleTaskDo;
import com.x.ai.common.dto.AgentScheduleCreateDto;
import com.x.ai.common.dto.AgentScheduleQueryDto;
import com.x.ai.common.dto.AgentScheduleUpdateDto;
import com.x.ai.common.model.AgentScheduleCreateParam;
import com.x.ai.common.model.AgentScheduleQueryParam;
import com.x.ai.common.model.AgentScheduleUpdateParam;
import com.x.ai.db.enums.AgentScheduleMode;
import com.x.ai.db.enums.AgentScheduleStatus;
import com.x.framework.common.response.BasePageResult;
import com.x.ai.service.AgentScheduleManagementService;
import com.x.ai.service.AgentScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentScheduleServiceImpl implements AgentScheduleService {

    private final AgentScheduleManagementService agentScheduleManagementService;



    @Override
    public AgentScheduleTaskDo create(AgentScheduleCreateDto request) {
        AgentScheduleCreateParam param = new AgentScheduleCreateParam()
                .setTaskName(request.getTaskName())
                .setModelId(request.getModelId())
                .setPrompt(request.getPrompt())
                .setScheduleMode(request.getScheduleMode())
                .setCronExpression(request.getCronExpression())
                .setFixedRateMs(request.getFixedRateMs())
                .setFixedDelayMs(request.getFixedDelayMs())
                .setInitialDelayMs(request.getInitialDelayMs());
        return agentScheduleManagementService.create(currentUserId(), param);
    }

    @Override
    public BasePageResult<List<AgentScheduleTaskDo>> page(AgentScheduleQueryDto request) {
        AgentScheduleQueryParam param = new AgentScheduleQueryParam()
                .setPage(request.getPage())
                .setPageSize(request.getPageSize())
                .setTaskName(request.getTaskName())
                .setScheduleMode(request.getScheduleMode() == null
                        ? null : AgentScheduleMode.fromCode(request.getScheduleMode()))
                .setStatus(request.getStatus() == null
                        ? null : AgentScheduleStatus.fromCode(request.getStatus()));
        return agentScheduleManagementService.page(currentUserId(), param);
    }

    @Override
    public AgentScheduleTaskDo get(Long id) {
        return agentScheduleManagementService.get(currentUserId(), id);
    }

    @Override
    public AgentScheduleTaskDo update(Long id, AgentScheduleUpdateDto request) {
        AgentScheduleUpdateParam param = new AgentScheduleUpdateParam()
                .setTaskName(request.getTaskName())
                .setModelId(request.getModelId())
                .setPrompt(request.getPrompt())
                .setScheduleMode(request.getScheduleMode())
                .setCronExpression(request.getCronExpression())
                .setFixedRateMs(request.getFixedRateMs())
                .setFixedDelayMs(request.getFixedDelayMs())
                .setInitialDelayMs(request.getInitialDelayMs());
        return agentScheduleManagementService.update(currentUserId(), id, param);
    }

    @Override
    public AgentScheduleOperationDo pause(Long id) {
        return agentScheduleManagementService.pause(currentUserId(), id);
    }

    @Override
    public AgentScheduleOperationDo resume(Long id) {
        return agentScheduleManagementService.resume(currentUserId(), id);
    }

    @Override
    public AgentScheduleOperationDo delete(Long id) {
        return agentScheduleManagementService.delete(currentUserId(), id);
    }

    @Override
    public AgentScheduleExecutionDo runNow(Long id) {
        return agentScheduleManagementService.runNow(currentUserId(), id);
    }

    @Override
    public BasePageResult<List<AgentScheduleExecutionDo>> executions(Long id, int page, int pageSize) {
        return agentScheduleManagementService.executions(currentUserId(), id, page, pageSize);
    }

    /**
     * 从可信请求上下文读取当前登录用户 ID。
     */
    private String currentUserId() {
        return UserThreadLocalUtil.getUserId();
    }
}
