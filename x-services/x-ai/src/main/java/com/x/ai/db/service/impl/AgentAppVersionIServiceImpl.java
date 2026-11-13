package com.x.ai.db.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.x.ai.db.entity.AgentAppVersion;
import com.x.ai.db.mapper.AgentAppVersionMapper;
import com.x.ai.db.service.AgentAppVersionIService;
import org.springframework.stereotype.Service;

@Service
public class AgentAppVersionIServiceImpl extends ServiceImpl<AgentAppVersionMapper, AgentAppVersion> implements AgentAppVersionIService {
}
