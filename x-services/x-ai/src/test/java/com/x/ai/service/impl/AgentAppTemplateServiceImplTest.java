package com.x.ai.service.impl;

import com.x.ai.common.domain.AgentAppDetailDo;
import com.x.ai.common.dto.AgentAppSaveDto;
import com.x.ai.service.AgentAppService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentAppTemplateServiceImplTest {

    private final AgentAppService applications = mock(AgentAppService.class);

    private final AgentAppTemplateServiceImpl service = new AgentAppTemplateServiceImpl(applications,
            new DefaultResourceLoader());

    @Test
    void discoversPackagedTypeScriptTemplatesWithoutRegisteringJavaApplications() {
        assertThat(service.list()).extracting(template -> template.getId()).containsExactly("color-lab", "http-lab");
        assertThat(service.list()).allSatisfy(template -> {
            assertThat(template.getName()).isNotBlank();
            assertThat(template.getDescription()).isNotBlank();
            assertThat(template.getPermissions()).contains("model.generate", "storage");
        });
        verifyNoInteractions(applications);
    }

    @Test
    void installingATemplateCreatesARegularOwnedApplicationThroughTheMinioPublishingPath() {
        AgentAppDetailDo expected = new AgentAppDetailDo();
        expected.setId("new-owned-app").setCurrentVersion("first-version");
        when(applications.create(eq("trusted-owner"), any())).thenReturn(expected);

        assertThat(service.install("trusted-owner", "http-lab")).isSameAs(expected);

        ArgumentCaptor<AgentAppSaveDto> input = ArgumentCaptor.forClass(AgentAppSaveDto.class);
        verify(applications).create(eq("trusted-owner"), input.capture());
        assertThat(input.getValue().getSource()).contains("app.http.request");
        assertThat(input.getValue().getHtml()).isNotBlank();
        assertThat(input.getValue().getCss()).isNotBlank();
        assertThat(input.getValue().getPermissions()).contains("http.request");
        assertThat(input.getValue().getBaseVersion()).isNull();
    }

    @Test
    void unknownTemplateIdentifiersCannotBeUsedAsResourcePaths() {
        assertThatThrownBy(() -> service.install("trusted-owner", "../../application.yml"))
                .isInstanceOfSatisfying(XException.class, error ->
                        assertThat(error.getResponseCodeEnum()).isEqualTo(ResponseCodeEnum.AGENT_APP_NOT_FOUND));
        verifyNoInteractions(applications);
    }
}
