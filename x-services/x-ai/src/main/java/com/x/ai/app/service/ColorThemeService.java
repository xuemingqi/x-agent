package com.x.ai.app.service;

import com.x.ai.common.domain.ColorThemeDo;
import com.x.ai.common.dto.ColorThemeGenerateDto;

public interface ColorThemeService {

    /**
     * 根据用户灵感生成或优化配色，并保证锁定槽位不会被模型修改。
     */
    ColorThemeDo generate(ColorThemeGenerateDto request);
}
