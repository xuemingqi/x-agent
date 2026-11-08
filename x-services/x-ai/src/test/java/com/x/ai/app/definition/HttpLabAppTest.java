package com.x.ai.app.definition;

import com.x.ai.app.service.AgentAppModelService;
import com.x.ai.common.domain.HttpAnalysisDo;
import com.x.ai.common.domain.HttpExecutionDo;
import com.x.ai.common.dto.HttpAnalysisDto;
import com.x.ai.common.model.HttpRequestField;
import com.x.ai.common.model.HttpRequestSpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpLabAppTest {

    @Test
    void passesTheSelectedSnapshotAndResponseDirectlyToTheUsersModel() {
        AgentAppModelService models = mock(AgentAppModelService.class);
        HttpLabApp application = new HttpLabApp(models);
        when(models.<HttpAnalysisDo>generate(eq("http-lab"), eq(42L), anyString(), any())).thenReturn(new HttpAnalysisDo());
        application.analyze(new HttpAnalysisDto().setModelId(42L).setQuestion("分析响应")
                .setRequest(new HttpRequestSpec().setUrl("http://example.test")
                        .setHeaders(List.of(new HttpRequestField().setName("Authorization").setValue("test-value"))))
                .setResponse(new HttpExecutionDo().setStatusCode(401).setBody("raw response").setElapsedMs(12.5)));
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(models).generate(eq("http-lab"), eq(42L), input.capture(), any());
        assertThat(input.getValue()).contains("test-value", "raw response", "12.5", "401").doesNotContain("modelId");
    }
}
