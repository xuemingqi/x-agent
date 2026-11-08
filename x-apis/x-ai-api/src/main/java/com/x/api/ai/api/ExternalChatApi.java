package com.x.api.ai.api;

import com.x.api.ai.api.config.ExternalChatApiFeignConfiguration;
import com.x.api.ai.domain.ExternalAgentResponseDo;
import com.x.api.ai.dto.ExternalAgentRequestDto;
import com.x.framework.common.response.BaseResult;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "x-ai",
        path = "/ai",
        contextId = "external-chat-api",
        configuration = ExternalChatApiFeignConfiguration.class)
public interface ExternalChatApi {

    /**
     * 使用 API Key 阻塞调用当前用户的默认 Agent 模型。
     *
     * @param request 外部 Agent 请求
     * @return Agent 执行结果
     */
    @PostMapping("/open/chat/run")
    BaseResult<ExternalAgentResponseDo> run(@RequestBody @Valid ExternalAgentRequestDto request);
}
