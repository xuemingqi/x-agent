package com.x.ai.app.service.impl;

import com.x.ai.app.definition.ColorLabApp;
import com.x.ai.app.service.AgentAppModelService;
import com.x.ai.common.domain.AgentAppDo;
import com.x.ai.common.domain.ColorThemeDo;
import com.x.ai.common.dto.ColorThemeGenerateDto;
import com.x.ai.common.model.AgentAppModelConfig;
import com.x.ai.common.model.ColorPalette;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ColorThemeServiceImplTest {

    @Test
    void restoresLockedColorsEvenWhenTheModelIgnoresThem() {
        AgentAppModelService models = mock(AgentAppModelService.class);
        ColorThemeServiceImpl service = new ColorThemeServiceImpl(new ColorLabApp(models));
        ColorPalette palette = new ColorPalette().setPrimary("#FFFFFF").setText("#111111");
        when(models.<ColorThemeDo>generate(eq("color-lab"), isNull(), anyString(), any()))
                .thenReturn(new ColorThemeDo().setPalette(palette));

        ColorThemeDo result = service.generate(new ColorThemeGenerateDto().setPrompt("森林")
                .setImproveReadability(true).setLockedColors(Map.of("primary", "#123456")));

        assertThat(result.getPalette().getPrimary()).isEqualTo("#123456");
        assertThat(result.getPalette().getText()).isEqualTo("#111111");
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AgentAppModelConfig<ColorThemeDo>> configuration = ArgumentCaptor.captor();
        verify(models).generate(eq("color-lab"), isNull(), input.capture(), configuration.capture());
        assertThat(input.getValue()).contains("\"improveReadability\":true", "#123456")
                .doesNotContain("modelId", "userId");
        assertThat(configuration.getValue().getSystemPrompt()).contains("AI 配色实验室", "lockedColors 是不可修改的硬约束");
        assertThat(configuration.getValue().getResultType()).isEqualTo(ColorThemeDo.class);
        assertThat(configuration.getValue().getMaxIterations()).isEqualTo(3);
        assertThat(configuration.getValue().getTimeout()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void discoversApplicationsThroughTheBaseClassAndRejectsDuplicateIdsAtStartup() {
        ColorLabApp application = new ColorLabApp(mock(AgentAppModelService.class));
        AgentAppServiceImpl registry = new AgentAppServiceImpl(List.of(application));

        assertThat(registry.list()).singleElement().satisfies(definition -> {
            assertThat(definition.getId()).isEqualTo("color-lab");
            assertThat(definition.getName()).isEqualTo("AI 配色实验室");
        });
        assertThatThrownBy(() -> new AgentAppServiceImpl(List.of(application, application)))
                .isInstanceOfSatisfying(XException.class, error ->
                        assertThat(error.getResponseCodeEnum()).isEqualTo(ResponseCodeEnum.AGENT_APP_REGISTRATION_ERROR));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ColorLab", "color_lab", "../app"})
    void rejectsInvalidApplicationIdsWithTheSystemException(String id) {
        ColorLabApp application = mock(ColorLabApp.class);
        when(application.definition()).thenReturn(new AgentAppDo().setId(id));

        assertThatThrownBy(() -> new AgentAppServiceImpl(List.of(application)))
                .isInstanceOfSatisfying(XException.class, error ->
                        assertThat(error.getResponseCodeEnum()).isEqualTo(ResponseCodeEnum.AGENT_APP_REGISTRATION_ERROR));
    }
}
