package com.x.ai.db.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.x.ai.db.entity.AgentScheduleExecution;
import com.x.ai.db.mapper.AgentScheduleExecutionMapper;
import com.x.ai.db.service.AgentScheduleExecutionIService;
import org.springframework.stereotype.Service;

@Service
public class AgentScheduleExecutionIServiceImpl extends ServiceImpl<AgentScheduleExecutionMapper, AgentScheduleExecution> implements AgentScheduleExecutionIService {
}
