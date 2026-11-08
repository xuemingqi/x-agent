package com.x.ai.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.x.ai.db.entity.AiModelConfig;
import com.x.ai.db.enums.AgentModelMultimodalCapability;
import com.x.ai.db.service.AiModelConfigIService;
import com.x.api.ai.dto.AiModelSaveDto;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.utils.UserThreadLocalUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiModelServiceImplTest {

    private final AiModelConfigIService modelIService = mock(AiModelConfigIService.class);

    private final Snowflake snowflake = mock(Snowflake.class);

    private final AiModelServiceImpl service = new AiModelServiceImpl(modelIService, snowflake);

    @BeforeEach
    void establishAuthenticatedUser() {
        UserThreadLocalUtil.set(UserInfo.builder().userId("42").build());
    }

    @AfterEach
    void clearAuthenticatedUser() {
        UserThreadLocalUtil.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void newDefaultModelOnlyClearsDefaultsOwnedByAuthenticatedUser() {
        LambdaUpdateChainWrapper<AiModelConfig> update = mock(LambdaUpdateChainWrapper.class, RETURNS_SELF);
        when(update.eq(any(), any())).thenReturn(update);
        when(update.ne(anyBoolean(), any(), any())).thenReturn(update);
        when(update.set(any(), any())).thenReturn(update);
        when(modelIService.lambdaUpdate()).thenReturn(update);
        when(snowflake.nextId()).thenReturn(99L);

        var result = service.create(request().setIsDefault(true).setMultimodalCapability(3));

        verify(update).eq(argThat(column -> Long.valueOf(42L)
                .equals(column.apply(new AiModelConfig().setUserId(42L)))), eq(42L));
        ArgumentCaptor<AiModelConfig> savedModel = ArgumentCaptor.forClass(AiModelConfig.class);
        verify(modelIService).save(savedModel.capture());
        assertEquals(42L, savedModel.getValue().getUserId());
        assertTrue(savedModel.getValue().getIsDefault());
        assertEquals(128000, savedModel.getValue().getContextWindowSize());
        assertEquals(128000, result.getData().getContextWindowSize());
        assertEquals(AgentModelMultimodalCapability.IMAGE_INPUT_OUTPUT, savedModel.getValue().getMultimodalCapability());
        assertEquals(3, result.getData().getMultimodalCapability());
    }

    @Test
    @SuppressWarnings("unchecked")
    void cannotUpdateAnotherUsersModelOrClearTheirDefault() {
        LambdaQueryChainWrapper<AiModelConfig> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(query.eq(any(), any())).thenReturn(query);
        when(modelIService.lambdaQuery()).thenReturn(query);

        var result = service.update(77L, request().setIsDefault(true));

        verify(query).eq(argThat(column -> Long.valueOf(42L)
                .equals(column.apply(new AiModelConfig().setUserId(42L)))), eq(42L));
        assertEquals("模型不存在", result.getMsg());
        verify(modelIService, never()).lambdaUpdate();
        verify(modelIService, never()).updateById(any(AiModelConfig.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void editingOwnModelPreservesBlankCredentialAndCanClearTemperature() {
        LambdaQueryChainWrapper<AiModelConfig> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(query.eq(any(), any())).thenReturn(query);
        when(modelIService.lambdaQuery()).thenReturn(query);
        AiModelConfig existing = new AiModelConfig().setId(99L).setUserId(42L)
                .setModelKey("existing-secret").setContextWindowSize(64000).setTemperature(0.5).setIsDefault(false);
        when(query.one()).thenReturn(existing);

        var result = service.update(99L, request().setModelKey(" ").setTemperature(null));

        assertEquals("existing-secret", existing.getModelKey());
        assertEquals(128000, existing.getContextWindowSize());
        assertNull(existing.getTemperature());
        assertNull(result.getData().getTemperature());
        verify(modelIService).updateById(existing);
        verify(modelIService, never()).lambdaUpdate();
    }

    private AiModelSaveDto request() {
        return new AiModelSaveDto().setName("My model").setModelName("provider-model")
                .setModelUrl("https://model.example/v1").setModelKey("private-key")
                .setContextWindowSize(128000).setTemperature(0.7);
    }
}
