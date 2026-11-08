package com.x.ai.controller;

import com.x.ai.app.service.ColorThemeService;
import com.x.ai.common.domain.ColorThemeDo;
import com.x.ai.common.dto.ColorThemeGenerateDto;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ColorThemeController", description = "AI 配色实验室")
@RestController
@RequestMapping("/apps/color-lab")
@RequiredArgsConstructor
public class ColorThemeController {

    private final ColorThemeService colorThemeService;


    @Operation(summary = "生成或优化配色方案")
    @PostMapping("/generate")
    public BaseResult<ColorThemeDo> generate(@RequestBody @Valid ColorThemeGenerateDto request) {
        return ResultUtil.buildResultSuccess(colorThemeService.generate(request));
    }
}
