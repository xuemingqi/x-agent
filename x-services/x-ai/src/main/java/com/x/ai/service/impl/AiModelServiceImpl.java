package com.x.ai.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.x.ai.db.entity.AiModelConfig;
import com.x.ai.db.enums.AgentModelMultimodalCapability;
import com.x.ai.db.service.AiModelConfigIService;
import com.x.ai.service.AiModelService;
import com.x.api.ai.domain.AiModelDo;
import com.x.api.ai.dto.AiModelSaveDto;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import com.x.framework.common.utils.UserThreadLocalUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : xuemingqi
 * @since : 2026-06-27 14:30
 */
@Service
@RequiredArgsConstructor(onConstructor = @__({@Autowired}))
public class AiModelServiceImpl implements AiModelService {

    private final AiModelConfigIService aiModelConfigIService;

    private final Snowflake snowflake;

    @Override
    public BaseResult<List<AiModelDo>> models() {
        Long owner = Long.valueOf(UserThreadLocalUtil.getUserId());
        List<AiModelDo> models = aiModelConfigIService.lambdaQuery()
                .eq(AiModelConfig::getUserId, owner)
                .orderByDesc(AiModelConfig::getUpdateTime)
                .list()
                .stream()
                .map(AiModelServiceImpl::toPublicDo)
                .toList();
        return ResultUtil.buildResultSuccess(models);
    }

    @Override
    @Transactional
    public BaseResult<AiModelDo> create(AiModelSaveDto request) {
        Long owner = Long.valueOf(UserThreadLocalUtil.getUserId());
        LocalDateTime now = LocalDateTime.now();
        if (!StringUtils.hasText(request.getModelKey())) {
            return ResultUtil.buildResultParamError("模型 Key 不能为空");
        }
        AiModelConfig config = AiModelConfig.builder()
                .id(snowflake.nextId())
                .userId(owner)
                .createTime(now)
                .build();
        config.setName(request.getName().trim())
                .setModelUrl(request.getModelUrl().trim())
                .setModelName(request.getModelName().trim())
                .setModelKey(request.getModelKey().trim())
                .setMultimodalCapability(AgentModelMultimodalCapability.fromCode(request.getMultimodalCapability()))
                .setContextWindowSize(request.getContextWindowSize())
                .setTemperature(request.getTemperature())
                .setIsDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .setUpdateTime(now);
        clearPreviousDefaultModel(owner, null, config.getIsDefault());
        aiModelConfigIService.save(config);
        return ResultUtil.buildResultSuccess(toPublicDo(config));
    }

    @Override
    @Transactional
    public BaseResult<AiModelDo> update(Long id, AiModelSaveDto request) {
        Long owner = Long.valueOf(UserThreadLocalUtil.getUserId());
        LocalDateTime now = LocalDateTime.now();
        AiModelConfig config = aiModelConfigIService.lambdaQuery()
                .eq(AiModelConfig::getId, id)
                .eq(AiModelConfig::getUserId, owner)
                .one();
        if (config == null) {
            return ResultUtil.buildResultParamError("模型不存在");
        }
        config.setName(request.getName().trim())
                .setModelUrl(request.getModelUrl().trim())
                .setModelName(request.getModelName().trim())
                .setModelKey(resolveModelKey(request.getModelKey(), config.getModelKey()))
                .setMultimodalCapability(AgentModelMultimodalCapability.fromCode(request.getMultimodalCapability()))
                .setContextWindowSize(request.getContextWindowSize())
                .setTemperature(request.getTemperature())
                .setIsDefault(request.getIsDefault() == null ? config.getIsDefault() : request.getIsDefault())
                .setUpdateTime(now);
        clearPreviousDefaultModel(owner, id, config.getIsDefault());
        aiModelConfigIService.updateById(config);
        return ResultUtil.buildResultSuccess(toPublicDo(config));
    }

    @Override
    public BaseResult<Void> delete(Long id) {
        Long owner = Long.valueOf(UserThreadLocalUtil.getUserId());
        aiModelConfigIService.lambdaUpdate()
                .eq(AiModelConfig::getId, id)
                .eq(AiModelConfig::getUserId, owner)
                .remove();
        return ResultUtil.buildResultSuccess();
    }

    static AiModelDo toPublicDo(AiModelConfig config) {
        AiModelDo model = new AiModelDo();
        BeanUtils.copyProperties(config, model);
        model.setMultimodalCapability(config.getMultimodalCapability().getCode());
        return model;
    }

    static String resolveModelKey(String submittedKey, String existingKey) {
        if (StringUtils.hasText(submittedKey)) {
            return submittedKey.trim();
        }
        return existingKey;
    }

    /**
     * 设置默认模型前清除当前用户已有的其他默认模型。
     */
    private void clearPreviousDefaultModel(Long owner, Long excludedModelId, Boolean setAsDefault) {
        if (!Boolean.TRUE.equals(setAsDefault)) {
            return;
        }
        aiModelConfigIService.lambdaUpdate()
                .eq(AiModelConfig::getUserId, owner)
                .eq(AiModelConfig::getIsDefault, true)
                .ne(excludedModelId != null, AiModelConfig::getId, excludedModelId)
                .set(AiModelConfig::getIsDefault, false)
                .update();
    }

}
