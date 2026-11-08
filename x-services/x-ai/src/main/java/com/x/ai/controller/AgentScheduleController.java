package com.x.ai.controller;

import com.x.ai.common.domain.AgentScheduleExecutionDo;
import com.x.ai.common.domain.AgentScheduleOperationDo;
import com.x.ai.common.domain.AgentScheduleTaskDo;
import com.x.ai.common.dto.AgentScheduleCreateDto;
import com.x.ai.common.dto.AgentScheduleQueryDto;
import com.x.ai.common.dto.AgentScheduleUpdateDto;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.response.BasePageResult;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import com.x.ai.service.AgentScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "AgentScheduleController", description = "定时任务管理")
@Validated

@RequestMapping("/schedule/tasks")
@RestController
@RequiredArgsConstructor
public class AgentScheduleController {

    private final AgentScheduleService agentScheduleService;


    @Operation(summary = "创建定时任务")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping
    public BaseResult<AgentScheduleTaskDo> create(@RequestBody @Valid AgentScheduleCreateDto request) {
        return ResultUtil.buildResultSuccess(agentScheduleService.create(request));
    }

    @Operation(summary = "分页查询定时任务")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @GetMapping
    public BaseResult<BasePageResult<List<AgentScheduleTaskDo>>> page(@ModelAttribute @Valid AgentScheduleQueryDto request) {
        return ResultUtil.buildResultSuccess(agentScheduleService.page(request));
    }

    @Operation(summary = "查询定时任务详情")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @GetMapping("/{id}")
    public BaseResult<AgentScheduleTaskDo> get(@PathVariable Long id) {
        return ResultUtil.buildResultSuccess(agentScheduleService.get(id));
    }

    @Operation(summary = "更新定时任务")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PutMapping("/{id}")
    public BaseResult<AgentScheduleTaskDo> update(@PathVariable Long id, @RequestBody @Valid AgentScheduleUpdateDto request) {
        return ResultUtil.buildResultSuccess(agentScheduleService.update(id, request));
    }

    @Operation(summary = "暂停定时任务")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping("/{id}/pause")
    public BaseResult<AgentScheduleOperationDo> pause(@PathVariable Long id) {
        return ResultUtil.buildResultSuccess(agentScheduleService.pause(id));
    }

    @Operation(summary = "恢复定时任务")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping("/{id}/resume")
    public BaseResult<AgentScheduleOperationDo> resume(@PathVariable Long id) {
        return ResultUtil.buildResultSuccess(agentScheduleService.resume(id));
    }

    @Operation(summary = "删除定时任务")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @DeleteMapping("/{id}")
    public BaseResult<AgentScheduleOperationDo> delete(@PathVariable Long id) {
        return ResultUtil.buildResultSuccess(agentScheduleService.delete(id));
    }

    @Operation(summary = "立即运行定时任务")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping("/{id}/run-now")
    public BaseResult<AgentScheduleExecutionDo> runNow(@PathVariable Long id) {
        return ResultUtil.buildResultSuccess(agentScheduleService.runNow(id));
    }

    @Operation(summary = "查询定时任务执行历史")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @GetMapping("/{id}/executions")
    public BaseResult<BasePageResult<List<AgentScheduleExecutionDo>>> executions(@PathVariable Long id, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ResultUtil.buildResultSuccess(agentScheduleService.executions(id, page, pageSize));
    }
}
