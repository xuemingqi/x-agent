package com.x.ai.controller;

import com.x.ai.common.domain.ExternalAccessConfigDo;
import com.x.ai.properties.AgentProperties;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/external-access")
@RequiredArgsConstructor
public class ExternalAccessController {

    private final AgentProperties agentProperties;

    @Operation(summary = "查询外部开发者接入地址")
    @GetMapping("/config")
    public BaseResult<ExternalAccessConfigDo> getConfig() {
        return ResultUtil.buildResultSuccess(new ExternalAccessConfigDo()
                .setHttpApiUrl(agentProperties.getExternalAccess().getHttpApiUrl())
                .setMcpServerUrl(agentProperties.getExternalAccess().getMcpServerUrl()));
    }
}
