package com.x.ai.mcp.gateway.service.impl;

import com.x.ai.mcp.gateway.service.AgentInvocationService;
import com.x.api.ai.api.ExternalChatApi;
import com.x.api.ai.domain.ExternalAgentResponseDo;
import com.x.api.ai.dto.ExternalAgentRequestDto;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.response.BaseResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentInvocationServiceImpl implements AgentInvocationService {

    private final ExternalChatApi externalChatApi;

    @Override
    public ExternalAgentResponseDo run(String question, String sessionId) {
        BaseResult<ExternalAgentResponseDo> result = externalChatApi.run(new ExternalAgentRequestDto()
                .setQuestion(question)
                .setSessionId(sessionId));
        if (result == null) {
            throw new XException(ResponseCodeEnum.AGENT_EXECUTION_ERROR);
        }
        if (!ResponseCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
            throw new XException(ResponseCodeEnum.fromCode(result.getCode())
                    .orElse(ResponseCodeEnum.AGENT_EXECUTION_ERROR));
        }
        if (result.getData() == null) {
            throw new XException(ResponseCodeEnum.AGENT_EXECUTION_ERROR);
        }
        return result.getData();
    }

}
