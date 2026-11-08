package com.x.ai.service;

import com.x.api.ai.domain.AiModelDo;
import com.x.api.ai.dto.AiModelSaveDto;
import com.x.framework.common.response.BaseResult;

import java.util.List;

/**
 * @author : xuemingqi
 * @since : 2026-06-27 14:30
 */
public interface AiModelService {

    BaseResult<List<AiModelDo>> models();

    BaseResult<AiModelDo> create(AiModelSaveDto request);

    BaseResult<AiModelDo> update(Long id, AiModelSaveDto request);

    BaseResult<Void> delete(Long id);
}
