package com.x.ai.db.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.x.ai.db.entity.AgentScheduleTask;
import com.x.ai.db.mapper.AgentScheduleTaskMapper;
import com.x.ai.db.service.AgentScheduleTaskIService;
import org.springframework.stereotype.Service;

@Service
public class AgentScheduleTaskIServiceImpl extends ServiceImpl<AgentScheduleTaskMapper, AgentScheduleTask> implements AgentScheduleTaskIService {
}
