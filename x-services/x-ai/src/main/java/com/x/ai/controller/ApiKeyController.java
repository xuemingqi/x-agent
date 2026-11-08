package com.x.ai.controller;

import com.x.ai.common.domain.ApiKeyDo;
import com.x.ai.service.ApiKeyService;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api-key")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @Operation(summary = "为当前登录用户申请或轮换 API Key")
    @Parameter(name = CommonConstant.TOKEN, description = "访问令牌", in = ParameterIn.HEADER, required = true)
    @PostMapping
    public BaseResult<ApiKeyDo> issueApiKey() {
        return ResultUtil.buildResultSuccess(apiKeyService.issue());
    }
}
