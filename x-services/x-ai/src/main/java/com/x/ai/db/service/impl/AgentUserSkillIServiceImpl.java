package com.x.ai.db.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.x.ai.db.entity.AgentUserSkill;
import com.x.ai.db.mapper.AgentUserSkillMapper;
import com.x.ai.db.service.AgentUserSkillIService;
import org.springframework.stereotype.Service;

@Service
public class AgentUserSkillIServiceImpl extends ServiceImpl<AgentUserSkillMapper, AgentUserSkill> implements AgentUserSkillIService {
}
