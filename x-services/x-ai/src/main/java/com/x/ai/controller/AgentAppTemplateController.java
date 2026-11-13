package com.x.ai.controller;

import com.x.ai.common.domain.AgentAppDetailDo;
import com.x.ai.common.domain.AgentAppTemplateDo;
import com.x.ai.service.AgentAppTemplateService;
import com.x.framework.common.annotation.LogIgnore;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AgentAppTemplateController", description = "TS 智能应用模板")
@RestController
@RequestMapping("/app-templates")
@RequiredArgsConstructor
public class AgentAppTemplateController {

    private final AgentAppTemplateService agentAppTemplateService;


    @Operation(summary = "查询应用模板")
    @GetMapping
    public BaseResult<List<AgentAppTemplateDo>> list() {
        return ResultUtil.buildResultSuccess(agentAppTemplateService.list());
    }

    @LogIgnore
    @Operation(summary = "从模板创建个人应用")
    @PostMapping("/{id}/install")
    public BaseResult<AgentAppDetailDo> install(@PathVariable String id) {
        return ResultUtil.buildResultSuccess(agentAppTemplateService.install(id));
    }
}
