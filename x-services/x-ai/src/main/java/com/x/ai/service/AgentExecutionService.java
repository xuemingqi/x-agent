package com.x.ai.service;

import com.x.ai.common.domain.AgentResponseDo;
import com.x.ai.common.dto.AgentRequestDto;
import com.x.ai.common.model.UserContext;
import reactor.core.publisher.Flux;


public interface AgentExecutionService {

    /**
     * 以指定用户身份同步执行一次 Agent 任务。
     *
     * @param userContext 可信用户上下文
     * @param request 智能体请求
     * @return 智能体执行结果
     */
    AgentResponseDo run(UserContext userContext, AgentRequestDto request);

    /**
     * 以指定用户身份同步执行一次独立任务，不记录为平台会话消息。
     *
     * @param userContext 可信用户上下文
     * @param request     智能体请求
     * @return 智能体执行结果
     */
    AgentResponseDo runStandalone(UserContext userContext, AgentRequestDto request);

    /**
     * 以指定用户身份流式执行一次 Agent 任务。
     *
     * @param userContext 可信用户上下文
     * @param request 智能体请求
     * @return 按事件顺序返回的智能体执行结果
     */
    Flux<AgentResponseDo> streamRun(UserContext userContext, AgentRequestDto request);
}
