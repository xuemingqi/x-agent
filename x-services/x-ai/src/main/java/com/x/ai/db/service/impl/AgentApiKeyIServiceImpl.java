package com.x.ai.db.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.x.ai.db.entity.AgentApiKey;
import com.x.ai.db.mapper.AgentApiKeyMapper;
import com.x.ai.db.service.AgentApiKeyIService;
import org.springframework.stereotype.Service;

@Service
public class AgentApiKeyIServiceImpl extends ServiceImpl<AgentApiKeyMapper, AgentApiKey>
        implements AgentApiKeyIService {
}
