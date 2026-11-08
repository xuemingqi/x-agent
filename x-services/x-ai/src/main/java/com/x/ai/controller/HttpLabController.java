package com.x.ai.controller;

import com.x.ai.app.definition.HttpLabApp;
import com.x.ai.app.service.HttpLoadTestService;
import com.x.ai.app.service.HttpRequestExecutionService;
import com.x.ai.common.domain.HttpAnalysisDo;
import com.x.ai.common.domain.HttpExecutionDo;
import com.x.ai.common.domain.HttpLoadResultDo;
import com.x.ai.common.dto.HttpAnalysisDto;
import com.x.ai.common.dto.HttpLoadTestDto;
import com.x.ai.common.model.HttpRequestSpec;
import com.x.framework.common.annotation.LogIgnore;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 调试载荷和 AI 分析可能包含第三方凭证，入口不记录完整请求与响应。
 */
@RestController
@RequestMapping("/apps/http-lab")
@RequiredArgsConstructor
@Tag(name = "HttpLabController", description = "HTTP 请求调试、分析与压测")
public class HttpLabController {

    private final HttpRequestExecutionService requestExecutionService;

    private final HttpLoadTestService loadTestService;

    private final HttpLabApp application;


    @Operation(summary = "发送 HTTP 调试请求", description = "根据请求配置发送一次 HTTP 请求，返回响应状态、响应头、正文及耗时。")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping("/send")
    @LogIgnore
    public Mono<BaseResult<HttpExecutionDo>> send(@RequestBody @Valid HttpRequestSpec request) {
        return requestExecutionService.execute(request).map(ResultUtil::buildResultSuccess);
    }

    @Operation(summary = "启动接口压测", description = "按并发数、持续时间和每秒速率上限启动压测，返回运行 ID 和初始指标。")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping("/runs")
    @LogIgnore
    public BaseResult<HttpLoadResultDo> start(@RequestBody @Valid HttpLoadTestDto request) {
        return ResultUtil.buildResultSuccess(loadTestService.start(request));
    }

    @Operation(summary = "查询压测运行状态", description = "查询当前用户的压测状态和实测指标，运行结果仅在当前节点内存中短暂保留。")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true),
            @Parameter(name = "id", description = "压测运行 ID", in = ParameterIn.PATH, required = true)
    })
    @GetMapping("/runs/{id}")
    @LogIgnore
    public BaseResult<HttpLoadResultDo> run(@PathVariable String id) {
        return ResultUtil.buildResultSuccess(loadTestService.get(id));
    }

    @Operation(summary = "停止接口压测", description = "停止当前用户指定的压测运行，取消正在执行的请求并返回最终指标。")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true),
            @Parameter(name = "id", description = "压测运行 ID", in = ParameterIn.PATH, required = true)
    })
    @PostMapping("/runs/{id}/stop")
    @LogIgnore
    public BaseResult<HttpLoadResultDo> stop(@PathVariable String id) {
        return ResultUtil.buildResultSuccess(loadTestService.stop(id));
    }

    @Operation(summary = "AI 分析 HTTP 请求", description = "使用当前用户选择的模型分析请求配置、调试响应或压测结果，返回观察结论及排查建议。")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping("/analyze")
    @LogIgnore
    public BaseResult<HttpAnalysisDo> analyze(@RequestBody @Valid HttpAnalysisDto request) {
        return ResultUtil.buildResultSuccess(application.analyze(request));
    }
}
