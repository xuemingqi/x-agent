package com.x.ai.controller;


import com.x.ai.common.domain.AgentResponseDo;
import com.x.ai.common.domain.TaskInterruptDo;
import com.x.ai.common.dto.AgentRequestDto;
import com.x.ai.common.dto.BrowserScreenshotResultDto;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import com.x.ai.service.BrowserScreenshotService;
import com.x.ai.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * @author : xuemingqi
 * @date : 2026/7/13 14:33
 */
@Tag(name = "ChatController", description = "ChatController")
@RequestMapping("/chat")
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    private final BrowserScreenshotService browserScreenshotService;


    @Operation(summary = "执行 Agent 能力")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping("/run")
    public BaseResult<AgentResponseDo> run(@RequestBody @Valid AgentRequestDto request) {
        return chatService.run(request);
    }

    @Operation(summary = "流式执行 Agent 能力")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping(value = "/stream/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<BaseResult<AgentResponseDo>> streamRun(@RequestBody @Valid AgentRequestDto request) {
        return chatService.streamRun(request);
    }

    @Operation(summary = "中断 Agent 任务")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping("/task/{taskId}/interrupt")
    public BaseResult<TaskInterruptDo> interrupt(@PathVariable String taskId) {
        return chatService.interrupt(taskId);
    }

    @Operation(summary = "回传用户授权的浏览器截图或拒绝结果")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true),
            @Parameter(name = "taskId", description = "截图请求所属任务 ID", in = ParameterIn.PATH, required = true),
            @Parameter(name = "requestId", description = "待处理截图请求 ID", in = ParameterIn.PATH, required = true)
    })
    @PostMapping("/task/{taskId}/screenshots/{requestId}")
    public BaseResult<Boolean> screenshot(@PathVariable String taskId, @PathVariable String requestId,
                                          @RequestBody @Valid BrowserScreenshotResultDto result) {
        browserScreenshotService.complete(taskId, requestId, result);
        return ResultUtil.buildResultSuccess(true);
    }
}
