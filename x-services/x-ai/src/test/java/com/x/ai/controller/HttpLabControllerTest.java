package com.x.ai.controller;

import com.x.ai.common.dto.HttpAnalysisDto;
import com.x.ai.common.dto.HttpLoadTestDto;
import com.x.ai.common.model.HttpRequestSpec;
import com.x.framework.common.annotation.LogIgnore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpLabControllerTest {

    @Test
    void neverLogsThirdPartyCredentialsInRequestsOrAnalysisResponses() throws NoSuchMethodException {
        assertThat(HttpLabController.class.getMethod("send", HttpRequestSpec.class)
                .isAnnotationPresent(LogIgnore.class)).isTrue();
        assertThat(HttpLabController.class.getMethod("start", HttpLoadTestDto.class)
                .isAnnotationPresent(LogIgnore.class)).isTrue();
        assertThat(HttpLabController.class.getMethod("run", String.class)
                .isAnnotationPresent(LogIgnore.class)).isTrue();
        assertThat(HttpLabController.class.getMethod("stop", String.class)
                .isAnnotationPresent(LogIgnore.class)).isTrue();
        assertThat(HttpLabController.class.getMethod("analyze", HttpAnalysisDto.class)
                .isAnnotationPresent(LogIgnore.class)).isTrue();
    }
}
