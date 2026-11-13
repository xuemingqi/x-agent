package com.x.ai.db.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.x.ai.db.entity.AgentApp;
import com.x.ai.db.mapper.AgentAppMapper;
import com.x.ai.db.service.AgentAppIService;
import org.springframework.stereotype.Service;

@Service
public class AgentAppIServiceImpl extends ServiceImpl<AgentAppMapper, AgentApp> implements AgentAppIService {

    @Override
    public AgentApp findForUpdate(String ownerUserId, String id) {
        return lambdaQuery().eq(AgentApp::getOwnerUserId, ownerUserId).eq(AgentApp::getId, id)
                .last("FOR UPDATE").one();
    }
}
