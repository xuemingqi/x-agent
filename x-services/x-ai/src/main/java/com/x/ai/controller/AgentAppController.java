package com.x.ai.controller;

import com.x.ai.app.service.AgentAppService;
import com.x.ai.common.domain.AgentAppDo;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AgentAppController", description = "智能应用中心")
@RestController
@RequestMapping("/apps")
@RequiredArgsConstructor
public class AgentAppController {

    private final AgentAppService agentAppService;


    @Operation(summary = "查询已注册的智能应用")
    @GetMapping
    public BaseResult<List<AgentAppDo>> list() {
        return ResultUtil.buildResultSuccess(agentAppService.list());
    }
}
