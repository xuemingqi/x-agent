package com.x.ai.controller;

import com.x.ai.service.ChatService;
import com.x.api.ai.api.ExternalChatApi;
import com.x.api.ai.domain.ExternalAgentResponseDo;
import com.x.api.ai.dto.ExternalAgentRequestDto;
import com.x.framework.common.annotation.LogIgnore;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.response.BaseResult;
import com.x.framework.web.annotation.ApiKeyAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ExternalChatController", description = "外部 Agent 调用")
@RequestMapping("/open/chat")
@RestController
@RequiredArgsConstructor
public class ExternalChatController implements ExternalChatApi {

    private final ChatService chatService;

    @Override
    @LogIgnore
    @ApiKeyAuthentication
    @Operation(summary = "使用 API Key 阻塞调用 Agent")
    @Parameter(name = CommonConstant.API_KEY_HEADER, description = "API Key", in = ParameterIn.HEADER, required = true)
    @PostMapping("/run")
    public BaseResult<ExternalAgentResponseDo> run(@RequestBody @Valid ExternalAgentRequestDto request) {
        return chatService.runWithDefaultModel(request);
    }
}
