package com.x.ai.service.impl;

import com.x.ai.common.domain.AgentResponseDo;
import com.x.ai.common.domain.TaskInterruptDo;
import com.x.ai.common.dto.AgentRequestDto;
import com.x.ai.common.enums.InterruptDispatch;
import com.x.ai.common.model.UserContext;
import com.x.ai.config.TaskInterruptCoordinator;
import com.x.ai.db.entity.AiModelConfig;
import com.x.ai.db.service.AiModelConfigIService;
import com.x.ai.service.AgentExecutionService;
import com.x.ai.service.ChatService;
import com.x.api.ai.domain.ExternalAgentResponseDo;
import com.x.api.ai.dto.ExternalAgentRequestDto;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.framework.common.utils.IdUtil;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final AgentExecutionService agentExecutionService;

    private final TaskInterruptCoordinator taskInterruptCoordinator;

    private final AiModelConfigIService aiModelConfigIService;

    @Override
    public BaseResult<AgentResponseDo> run(AgentRequestDto request) {
        return ResultUtil.buildResultSuccess(agentExecutionService.run(currentUser(), request));
    }

    @Override
    public BaseResult<ExternalAgentResponseDo> runWithDefaultModel(ExternalAgentRequestDto request) {
        UserContext user = currentUser();
        AiModelConfig model = aiModelConfigIService.lambdaQuery()
                .eq(AiModelConfig::getUserId, Long.valueOf(user.getUserId()))
                .eq(AiModelConfig::getIsDefault, true)
                .one();
        if (model == null) {
            throw new XException(ResponseCodeEnum.AGENT_MODEL_UNAVAILABLE);
        }
        String sessionId = StringUtil.isNotBlank(request.getSessionId())
                ? request.getSessionId()
                : IdUtil.fastUUID();
        AgentResponseDo response = agentExecutionService.run(user, new AgentRequestDto()
                .setRequest(request.getQuestion())
                .setSessionId(sessionId)
                .setModelId(model.getId()));
        return ResultUtil.buildResultSuccess(new ExternalAgentResponseDo()
                .setText(response.getText())
                .setSessionId(response.getSessionId())
                .setUserId(response.getUserId()));
    }

    @Override
    public Flux<BaseResult<AgentResponseDo>> streamRun(AgentRequestDto request) {
        return agentExecutionService.streamRun(currentUser(), request)
                .map(ResultUtil::buildResultSuccess)
                .onErrorResume(error ->
                        Flux.just(ResultUtil.buildResultError(ResponseCodeEnum.AGENT_EXECUTION_ERROR)));
    }

    @Override
    public BaseResult<TaskInterruptDo> interrupt(String taskId) {
        UserContext user = currentUser();
        InterruptDispatch dispatch = taskInterruptCoordinator.interrupt(taskId, user.getUserId());
        return ResultUtil.buildResultSuccess(new TaskInterruptDo()
                .setTaskId(taskId)
                .setDispatch(dispatch)
                .setAccepted(true));
    }

    /**
     * 从当前请求线程构建用户上下文。
     */
    private UserContext currentUser() {
        return new UserContext()
                .setUserId(UserThreadLocalUtil.getUserId());
    }
}
