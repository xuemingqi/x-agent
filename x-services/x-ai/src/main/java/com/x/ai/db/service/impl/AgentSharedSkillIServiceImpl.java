package com.x.ai.db.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.x.ai.db.entity.AgentSharedSkill;
import com.x.ai.db.mapper.AgentSharedSkillMapper;
import com.x.ai.db.service.AgentSharedSkillIService;
import org.springframework.stereotype.Service;

@Service
public class AgentSharedSkillIServiceImpl extends ServiceImpl<AgentSharedSkillMapper, AgentSharedSkill> implements AgentSharedSkillIService {
}
