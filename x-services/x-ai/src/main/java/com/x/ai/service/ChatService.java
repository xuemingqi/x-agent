package com.x.ai.service;

import com.x.ai.common.domain.AgentResponseDo;
import com.x.ai.common.domain.TaskInterruptDo;
import com.x.ai.common.dto.AgentRequestDto;
import com.x.api.ai.domain.ExternalAgentResponseDo;
import com.x.api.ai.dto.ExternalAgentRequestDto;
import com.x.framework.common.response.BaseResult;
import reactor.core.publisher.Flux;

/**
 * @author : xuemingqi
 * @date : 2026/7/13 14:39
 */
public interface ChatService {

    /**
     * 同步执行一次 AgentScope 智能体任务。
     *
     * @param request 智能体请求
     * @return 智能体执行结果
     */
    BaseResult<AgentResponseDo> run(AgentRequestDto request);

    /**
     * 使用当前 API Key 用户的默认模型同步执行任务。
     */
    BaseResult<ExternalAgentResponseDo> runWithDefaultModel(ExternalAgentRequestDto request);

    /**
     * 流式执行一次 AgentScope 智能体任务。
     *
     * @param request 智能体请求
     * @return 按事件顺序返回的智能体执行结果
     */
    Flux<BaseResult<AgentResponseDo>> streamRun(AgentRequestDto request);

    /**
     * 中断指定的运行中智能体任务。
     *
     * @param taskId 任务标识
     * @return 中断受理结果
     */
    BaseResult<TaskInterruptDo> interrupt(String taskId);
}
