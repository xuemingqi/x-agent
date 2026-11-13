package com.x.ai.controller;

import com.x.ai.service.AgentAppRuntimeService;
import com.x.ai.common.domain.AgentAppGenerateDo;
import com.x.ai.common.domain.AgentAppRunDo;
import com.x.ai.common.domain.HttpExecutionDo;
import com.x.ai.common.dto.AgentAppDataSaveDto;
import com.x.ai.common.dto.AgentAppGenerateDto;
import com.x.ai.common.dto.AgentAppRunDto;
import com.x.ai.common.dto.AgentAppHttpRequestDto;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;

@Tag(name = "AgentAppRuntimeController", description = "智能应用受控能力接口")
@RestController
@RequestMapping("/apps/{id}")
@RequiredArgsConstructor
public class AgentAppRuntimeController {

    private final AgentAppRuntimeService agentAppRuntimeService;


    @LogIgnore
    @Operation(summary = "转发应用显式配置的 HTTP 请求")
    @PostMapping("/runtime/http")
    public Mono<BaseResult<HttpExecutionDo>> requestHttp(@PathVariable String id,
                                                        @RequestBody @Valid AgentAppHttpRequestDto request) {
        return agentAppRuntimeService.requestHttp(id, request).map(ResultUtil::buildResultSuccess);
    }


    @LogIgnore
    @Operation(summary = "使用当前用户的模型生成文本")
    @PostMapping("/runtime/generate")
    public BaseResult<AgentAppGenerateDo> generate(@PathVariable String id,
                                                 @RequestBody @Valid AgentAppGenerateDto request) {
        return ResultUtil.buildResultSuccess(agentAppRuntimeService.generate(id, request));
    }

    @LogIgnore
    @Operation(summary = "使用当前用户已启用的 MCP 执行独立任务")
    @PostMapping("/runtime/run")
    public BaseResult<AgentAppRunDo> run(@PathVariable String id, @RequestBody @Valid AgentAppRunDto request) {
        return ResultUtil.buildResultSuccess(agentAppRuntimeService.run(id, request));
    }

    @LogIgnore
    @Operation(summary = "读取当前用户的应用数据")
    @GetMapping("/data/{key}")
    public BaseResult<JsonNode> getData(@PathVariable String id, @PathVariable String key,
                                        @RequestParam String version) {
        return ResultUtil.buildResultSuccess(agentAppRuntimeService.getData(id, version, key));
    }

    @LogIgnore
    @Operation(summary = "保存当前用户的应用数据")
    @PutMapping("/data/{key}")
    public BaseResult<Boolean> saveData(@PathVariable String id, @PathVariable String key,
                                        @RequestParam String version, @RequestBody @Valid AgentAppDataSaveDto request) {
        return ResultUtil.buildResultSuccess(agentAppRuntimeService.saveData(id, version, key, request));
    }

    @LogIgnore
    @Operation(summary = "删除当前用户的应用数据")
    @DeleteMapping("/data/{key}")
    public BaseResult<Boolean> deleteData(@PathVariable String id, @PathVariable String key,
                                          @RequestParam String version) {
        return ResultUtil.buildResultSuccess(agentAppRuntimeService.deleteData(id, version, key));
    }
}
