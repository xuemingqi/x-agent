package com.x.ai.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.x.ai.db.entity.AiModelConfig;
import com.x.ai.db.enums.AgentModelMultimodalCapability;
import com.x.ai.db.service.AiModelConfigIService;
import com.x.api.ai.dto.AiModelSaveDto;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiModelRuntimeServiceImplTest {

    private final AiModelConfigIService models = mock();

    private final LambdaQueryChainWrapper<AiModelConfig> query = mock();

    private final AiModelRuntimeServiceImpl service = new AiModelRuntimeServiceImpl(models);

    @BeforeEach
    void prepareQuery() {
        when(models.lambdaQuery()).thenReturn(query);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.eq(anyBoolean(), any(), any())).thenReturn(query);
    }

    @Test
    void explicitModelAlwaysQueriesAuthenticatedOwnerAndPreservesCapabilities() {
        when(query.one()).thenReturn(model().setMultimodalCapability(AgentModelMultimodalCapability.IMAGE_INPUT_OUTPUT));

        var runtime = service.requireRuntimeModel("42", 99L);

        verify(query).eq(argThat(column -> Long.valueOf(42).equals(column.apply(new AiModelConfig().setUserId(42L)))), eq(42L));
        verify(query).eq(eq(true), argThat(column -> Long.valueOf(99).equals(column.apply(new AiModelConfig().setId(99L)))), eq(99L));
        assertThat(runtime.getMultimodalCapability()).isEqualTo(AgentModelMultimodalCapability.IMAGE_INPUT_OUTPUT);
        assertThat(runtime.getApiKey()).isEqualTo("test-model-secret");
        assertThat(runtime.toString()).doesNotContain("test-model-secret");
    }

    @Test
    void defaultModelIsSelectedOnlyWithinTheRequestedUser() {
        when(query.one()).thenReturn(model());

        service.requireRuntimeModel("42", null);

        verify(query).eq(argThat(column -> Long.valueOf(42).equals(column.apply(new AiModelConfig().setUserId(42L)))), eq(42L));
        verify(query).eq(eq(true), argThat(column -> Boolean.TRUE.equals(column.apply(new AiModelConfig().setIsDefault(true)))), eq(true));
    }

    @Test
    void missingOrAnotherUsersModelCannotBeUsedByAppsOrSkillImports() {
        assertThatThrownBy(() -> service.requireRuntimeModel("42", 7L))
                .isInstanceOfSatisfying(XException.class,
                        error -> assertThat(error.getResponseCodeEnum()).isEqualTo(ResponseCodeEnum.AGENT_MODEL_UNAVAILABLE));
    }

    @Test
    void invalidContextWindowIsNeverReplacedWithAGuessedDefault() {
        when(query.one()).thenReturn(model().setContextWindowSize(0));

        assertThatThrownBy(() -> service.requireRuntimeModel("42", 99L))
                .isInstanceOfSatisfying(XException.class,
                        error -> assertThat(error.getResponseCodeEnum()).isEqualTo(ResponseCodeEnum.AGENT_MODEL_UNAVAILABLE));
    }

    @Test
    void modelApiRejectsUnknownAndNullCapabilityCodes() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validateValue(AiModelSaveDto.class, "multimodalCapability", 4)).isNotEmpty();
            assertThat(validator.validateValue(AiModelSaveDto.class, "multimodalCapability", null)).isNotEmpty();
            assertThat(validator.validateValue(AiModelSaveDto.class, "multimodalCapability", 3)).isEmpty();
        }
    }

    private AiModelConfig model() {
        return new AiModelConfig().setId(99L).setUserId(42L).setModelName("test-model")
                .setModelUrl("https://model.example/v1").setModelKey("test-model-secret")
                .setContextWindowSize(128000).setIsDefault(true);
    }
}
