package com.x.ai.service.impl;

import com.x.ai.common.model.AiModelRuntimeConfig;
import com.x.ai.db.entity.AiModelConfig;
import com.x.ai.db.service.AiModelConfigIService;
import com.x.ai.service.AiModelRuntimeService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiModelRuntimeServiceImpl implements AiModelRuntimeService {

    private final AiModelConfigIService aiModelConfigIService;

    @Override
    public AiModelRuntimeConfig requireRuntimeModel(String userId, Long modelId) {
        if (StringUtil.isBlank(userId)) {
            throw new XException(ResponseCodeEnum.AGENT_MODEL_UNAVAILABLE);
        }
        AiModelConfig model = aiModelConfigIService.lambdaQuery()
                .eq(AiModelConfig::getUserId, StringUtil.parseLongId(userId))
                .eq(modelId != null, AiModelConfig::getId, modelId)
                .eq(modelId == null, AiModelConfig::getIsDefault, true)
                .one();
        if (model == null || StringUtil.isBlank(model.getModelKey()) || StringUtil.isBlank(model.getModelUrl())
                || StringUtil.isBlank(model.getModelName()) || model.getContextWindowSize() == null
                || model.getContextWindowSize() <= 0) {
            throw new XException(ResponseCodeEnum.AGENT_MODEL_UNAVAILABLE);
        }
        return new AiModelRuntimeConfig().setModelId(model.getId()).setModelName(model.getModelName())
                .setBaseUrl(model.getModelUrl()).setApiKey(model.getModelKey())
                .setContextWindowSize(model.getContextWindowSize()).setTemperature(model.getTemperature())
                .setMultimodalCapability(model.getMultimodalCapability());
    }
}
