package com.x.ai.app.service.impl;

import com.x.ai.common.domain.ColorThemeDo;
import com.x.ai.common.dto.ColorThemeGenerateDto;
import com.x.ai.common.model.AgentAppModelConfig;
import com.x.ai.common.model.ColorPalette;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.ai.common.model.AiModelRuntimeConfig;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.JsonUtil;
import com.x.ai.service.AiModelRuntimeService;
import io.agentscope.core.model.transport.HttpRequest;
import io.agentscope.core.model.transport.HttpResponse;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.model.transport.HttpTransportFactory;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentAppModelServiceImplTest {

    private final AiModelRuntimeService settings = mock(AiModelRuntimeService.class);

    private final HttpTransport transport = mock(HttpTransport.class);

    private HttpTransport originalTransport;

    private ValidatorFactory validation;

    private AgentAppModelServiceImpl service;

    @BeforeEach
    void setUp() {
        originalTransport = HttpTransportFactory.getDefault();
        HttpTransportFactory.setDefault(transport);
        validation = Validation.buildDefaultValidatorFactory();
        service = new AgentAppModelServiceImpl(settings, validation.getValidator());
        UserThreadLocalUtil.set(UserInfo.builder().userId("1").build());
        when(settings.requireRuntimeModel("1", null)).thenReturn(new AiModelRuntimeConfig()
                .setModelId(42L).setModelName("schema-test").setBaseUrl("https://model.example.test/v1")
                .setApiKey("test-only-key").setContextWindowSize(8192).setTemperature(0.5));
    }

    @AfterEach
    void tearDown() {
        HttpTransportFactory.setDefault(originalTransport);
        validation.close();
        UserThreadLocalUtil.clear();
    }

    @Test
    void sendsFieldDescriptionsAndDeserializesDifferentClassesWithoutSharingConversationHistory() throws Exception {
        ColorThemeDo theme = new ColorThemeDo().setName("森林").setDescription("清新的森林配色").setPalette(palette());
        when(transport.execute(any())).thenReturn(response(theme), response(palette()));

        ColorThemeDo result = service.generate("color-lab", null, "第一位用户的森林", configuration("只生成配色", ColorThemeDo.class));
        UserThreadLocalUtil.set(UserInfo.builder().userId("2").build());
        when(settings.requireRuntimeModel("2", 84L)).thenReturn(new AiModelRuntimeConfig()
                .setModelId(84L).setModelName("second-model").setBaseUrl("https://model.example.test/v1")
                .setApiKey("second-test-key").setContextWindowSize(8192).setTemperature(0.5));
        ColorPalette second = service.generate("other-app", 84L, "另一次独立调用", configuration("只生成颜色", ColorPalette.class));

        assertThat(result).isEqualTo(theme);
        assertThat(second).isEqualTo(palette());
        verify(settings).requireRuntimeModel("1", null);
        verify(settings).requireRuntimeModel("2", 84L);
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(transport, times(2)).execute(requests.capture());
        String firstBody = requests.getAllValues().getFirst().getBody();
        String secondBody = requests.getAllValues().getLast().getBody();
        assertThat(firstBody).contains("json_schema", "页面背景色", "palette", "森林");
        var schema = JsonUtil.OBJECT_MAPPER.readTree(firstBody).path("response_format").path("json_schema");
        assertThat(schema.path("strict").asBoolean()).isTrue();
        assertThat(schema.path("schema").path("required").toString()).contains("name", "description", "palette");
        assertThat(secondBody).contains("json_schema", "second-model", "background", "另一次独立调用")
                .doesNotContain("第一位用户的森林", "palette", "shell", "filesystem");
        verify(transport, never()).close();
    }

    @Test
    void rejectsStructurallyValidButUnsafeColorValues() {
        ColorThemeDo invalid = new ColorThemeDo().setName("错误").setDescription("错误的颜色")
                .setPalette(palette().setPrimary("red; background:url(example)"));
        when(transport.execute(any())).thenReturn(response(invalid));

        assertThatThrownBy(() -> service.generate("color-lab", null, "森林", configuration("配色", ColorThemeDo.class)))
                .isInstanceOfSatisfying(XException.class, error ->
                        assertThat(error.getResponseCodeEnum()).isEqualTo(ResponseCodeEnum.AGENT_APP_OUTPUT_INVALID));
    }

    @Test
    void preservesMissingModelConfigurationErrorBeforeCallingTheGateway() {
        when(settings.requireRuntimeModel("1", null))
                .thenThrow(new XException(ResponseCodeEnum.AGENT_MODEL_UNAVAILABLE));

        assertThatThrownBy(() -> service.generate("color-lab", null, "森林", configuration("配色", ColorThemeDo.class)))
                .isInstanceOfSatisfying(XException.class, error ->
                        assertThat(error.getResponseCodeEnum()).isEqualTo(ResponseCodeEnum.AGENT_MODEL_UNAVAILABLE));
        verifyNoInteractions(transport);
    }

    @Test
    void returnsAnApplicationErrorWhenTheGatewayRejectsStructuredOutput() {
        when(transport.execute(any())).thenReturn(HttpResponse.builder().statusCode(400)
                .body("{\"error\":{\"message\":\"response_format is unsupported\",\"type\":\"invalid_request_error\"}}")
                .build());

        assertThatThrownBy(() -> service.generate("color-lab", null, "森林", configuration("配色", ColorThemeDo.class)))
                .isInstanceOfSatisfying(XException.class, error ->
                        assertThat(error.getResponseCodeEnum()).isEqualTo(ResponseCodeEnum.AGENT_APP_GENERATION_ERROR));
    }

    @Test
    void usesFrameworkToolFallbackWhenTheGatewayRejectsNativeSchema() {
        ColorThemeDo theme = new ColorThemeDo().setName("森林").setDescription("兼容模式生成的配色").setPalette(palette());
        HttpResponse toolResponse = HttpResponse.builder().statusCode(200).body(JsonUtil.toJsonStrOrThrow(Map.of(
                "id", "tool-response", "model", "schema-test", "object", "chat.completion",
                "choices", List.of(Map.of("index", 0, "finish_reason", "tool_calls", "message", Map.of(
                        "role", "assistant", "tool_calls", List.of(Map.of("id", "result-1", "type", "function",
                                "function", Map.of("name", "generate_response", "arguments",
                                        JsonUtil.toJsonStrOrThrow(Map.of("response", theme)))))))))))
                .build();
        when(transport.execute(any())).thenReturn(HttpResponse.builder().statusCode(400)
                .body("{\"error\":{\"message\":\"response_format is unsupported\"}}")
                .build(), toolResponse);

        assertThat(service.generate("color-lab", null, "森林", configuration("配色", ColorThemeDo.class))).isEqualTo(theme);
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(transport, times(2)).execute(requests.capture());
        assertThat(requests.getAllValues().getLast().getBody()).contains("generate_response", "页面背景色");
    }

    @Test
    void rejectsUnknownLockSlotsAndInvalidNestedPaletteAtTheRequestBoundary() {
        ColorThemeGenerateDto request = new ColorThemeGenerateDto().setPrompt("森林")
                .setLockedColors(Map.of("background-image", "#123456"))
                .setCurrentPalette(palette().setSurface("#fff"));

        assertThat(validation.getValidator().validate(request)).hasSize(2);
    }

    private <T> AgentAppModelConfig<T> configuration(String systemPrompt, Class<T> resultType) {
        return AgentAppModelConfig.<T>builder().systemPrompt(systemPrompt).resultType(resultType)
                .maxIterations(3).timeout(Duration.ofSeconds(5)).build();
    }

    private ColorPalette palette() {
        return new ColorPalette().setBackground("#EFF3EE").setSurface("#FFFFFF").setPrimary("#346650")
                .setOnPrimary("#FFFFFF").setText("#223C2E").setMutedText("#5C6E61").setBorder("#C8D5C9");
    }

    private HttpResponse response(Object result) {
        return HttpResponse.builder().statusCode(200).body(JsonUtil.toJsonStrOrThrow(Map.of(
                "id", "test-response", "model", "schema-test", "object", "chat.completion",
                "choices", List.of(Map.of("index", 0, "finish_reason", "stop", "message", Map.of(
                        "role", "assistant", "content", JsonUtil.toJsonStrOrThrow(result))))))).build();
    }
}
