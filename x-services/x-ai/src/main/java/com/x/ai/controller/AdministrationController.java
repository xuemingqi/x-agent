package com.x.ai.controller;

import com.x.ai.common.domain.AdministrationStatusDo;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import com.x.ai.service.AdministratorAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/administration")
@RequiredArgsConstructor
public class AdministrationController {

    private final AdministratorAuthorizationService authorizationService;

    @Operation(summary = "查询当前登录用户的管理员状态")
    @Parameter(name = CommonConstant.TOKEN, description = "访问令牌", in = ParameterIn.HEADER, required = true)
    @GetMapping("/status")
    public BaseResult<AdministrationStatusDo> status() {
        return ResultUtil.buildResultSuccess(authorizationService.currentStatus());
    }
}
