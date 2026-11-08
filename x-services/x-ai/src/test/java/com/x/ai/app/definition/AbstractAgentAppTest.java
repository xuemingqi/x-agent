package com.x.ai.app.definition;

import com.x.ai.app.service.AgentAppModelService;
import com.x.ai.common.domain.ColorThemeDo;
import com.x.ai.common.model.AgentAppModelConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractAgentAppTest {

    @Test
    void inheritedExecutionUsesTheDefinitionsAndLimitsOverriddenByTheApplication() {
        AgentAppModelService models = mock(AgentAppModelService.class);
        ColorLabApp application = spy(new ColorLabApp(models));
        doReturn("应用覆盖后的提示词").when(application).systemPrompt();
        doReturn(5).when(application).maxIterations();
        doReturn(Duration.ofSeconds(30)).when(application).timeout();
        ColorThemeDo expected = new ColorThemeDo().setName("森林");
        when(models.<ColorThemeDo>generate(eq("color-lab"), eq(42L), eq("业务输入"), any())).thenReturn(expected);

        ColorThemeDo result = application.generate(42L, "业务输入");

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<AgentAppModelConfig<ColorThemeDo>> configuration = ArgumentCaptor.captor();
        verify(models).generate(eq("color-lab"), eq(42L), eq("业务输入"), configuration.capture());
        assertThat(configuration.getValue().getSystemPrompt()).isEqualTo("应用覆盖后的提示词");
        assertThat(configuration.getValue().getResultType()).isEqualTo(ColorThemeDo.class);
        assertThat(configuration.getValue().getMaxIterations()).isEqualTo(5);
        assertThat(configuration.getValue().getTimeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
