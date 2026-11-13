package com.x.ai.controller;

import com.x.ai.service.AgentAppService;
import com.x.ai.common.domain.AgentAppDetailDo;
import com.x.ai.common.domain.AgentAppDo;
import com.x.ai.common.domain.AgentAppVersionDo;
import com.x.ai.common.dto.AgentAppActivateDto;
import com.x.ai.common.dto.AgentAppSaveDto;
import com.x.framework.common.annotation.LogIgnore;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AgentAppController", description = "智能应用中心")
@RestController
@RequestMapping("/apps")
@RequiredArgsConstructor
public class AgentAppController {

    private final AgentAppService agentAppService;


    @Operation(summary = "查询个人智能应用")
    @GetMapping
    public BaseResult<List<AgentAppDo>> list() {
        return ResultUtil.buildResultSuccess(agentAppService.list());
    }

    @LogIgnore
    @Operation(summary = "读取个人应用与当前版本源码")
    @GetMapping("/{id}")
    public BaseResult<AgentAppDetailDo> get(@PathVariable String id) {
        return ResultUtil.buildResultSuccess(agentAppService.get(id));
    }

    @LogIgnore
    @Operation(summary = "创建并发布个人应用")
    @PostMapping
    public BaseResult<AgentAppDetailDo> create(@RequestBody @Valid AgentAppSaveDto request) {
        return ResultUtil.buildResultSuccess(agentAppService.create(request));
    }

    @LogIgnore
    @Operation(summary = "发布个人应用的新版本")
    @PutMapping("/{id}")
    public BaseResult<AgentAppDetailDo> update(@PathVariable String id, @RequestBody @Valid AgentAppSaveDto request) {
        return ResultUtil.buildResultSuccess(agentAppService.update(id, request));
    }

    @Operation(summary = "查询个人应用的历史版本")
    @GetMapping("/{id}/versions")
    public BaseResult<List<AgentAppVersionDo>> versions(@PathVariable String id) {
        return ResultUtil.buildResultSuccess(agentAppService.versions(id));
    }

    @LogIgnore
    @Operation(summary = "切换个人应用的发布版本")
    @PostMapping("/{id}/versions/{version}/activate")
    public BaseResult<AgentAppDetailDo> activate(@PathVariable String id, @PathVariable String version,
                                                @RequestBody @Valid AgentAppActivateDto request) {
        return ResultUtil.buildResultSuccess(agentAppService.activate(id, version, request.getBaseVersion()));
    }

    @Operation(summary = "删除个人应用及版本文件")
    @DeleteMapping("/{id}")
    public BaseResult<Boolean> delete(@PathVariable String id) {
        return ResultUtil.buildResultSuccess(agentAppService.delete(id));
    }
}
