package com.x.ai.db.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.x.ai.db.entity.AgentAppData;
import com.x.ai.db.mapper.AgentAppDataMapper;
import com.x.ai.db.service.AgentAppDataIService;
import org.springframework.stereotype.Service;

@Service
public class AgentAppDataIServiceImpl extends ServiceImpl<AgentAppDataMapper, AgentAppData> implements AgentAppDataIService {

    @Override
    public void upsert(AgentAppData data) {
        baseMapper.upsert(data);
    }
}
