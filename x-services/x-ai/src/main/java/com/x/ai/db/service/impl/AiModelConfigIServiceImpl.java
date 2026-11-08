package com.x.ai.db.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.x.ai.db.entity.AiModelConfig;
import com.x.ai.db.mapper.AiModelConfigMapper;
import com.x.ai.db.service.AiModelConfigIService;
import org.springframework.stereotype.Service;

/**
 * @author : xuemingqi
 * @since : 2026-06-27 14:30
 */
@Service
public class AiModelConfigIServiceImpl extends ServiceImpl<AiModelConfigMapper, AiModelConfig>
        implements AiModelConfigIService {
}
