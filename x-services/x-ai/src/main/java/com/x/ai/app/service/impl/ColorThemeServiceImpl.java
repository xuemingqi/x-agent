package com.x.ai.app.service.impl;

import com.x.ai.app.definition.ColorLabApp;
import com.x.ai.app.service.ColorThemeService;
import com.x.ai.common.domain.ColorThemeDo;
import com.x.ai.common.dto.ColorThemeGenerateDto;
import com.x.ai.common.model.ColorPalette;
import com.x.framework.common.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ColorThemeServiceImpl implements ColorThemeService {

    private final ColorLabApp application;


    @Override
    public ColorThemeDo generate(ColorThemeGenerateDto request) {
        ColorThemeDo result = application.generate(request.getModelId(), buildInput(request));
        preserveLockedColors(result.getPalette(), request.getLockedColors());
        return result;
    }

    /**
     * 只向模型提供配色所需的数据，不发送模型选择或用户身份。
     */
    private String buildInput(ColorThemeGenerateDto request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", request.getPrompt());
        input.put("dark", request.isDark());
        input.put("vivid", request.isVivid());
        input.put("improveReadability", request.isImproveReadability());
        input.put("currentPalette", request.getCurrentPalette());
        input.put("lockedColors", request.getLockedColors());
        return JsonUtil.toJsonStrOrThrow(input);
    }

    /**
     * 锁定是应用的确定性规则，不依赖模型是否遵从提示词。
     */
    private void preserveLockedColors(ColorPalette palette, Map<String, String> lockedColors) {
        palette.setBackground(lockedColors.getOrDefault("background", palette.getBackground()))
                .setSurface(lockedColors.getOrDefault("surface", palette.getSurface()))
                .setPrimary(lockedColors.getOrDefault("primary", palette.getPrimary()))
                .setOnPrimary(lockedColors.getOrDefault("onPrimary", palette.getOnPrimary()))
                .setText(lockedColors.getOrDefault("text", palette.getText()))
                .setMutedText(lockedColors.getOrDefault("mutedText", palette.getMutedText()))
                .setBorder(lockedColors.getOrDefault("border", palette.getBorder()));
    }
}
