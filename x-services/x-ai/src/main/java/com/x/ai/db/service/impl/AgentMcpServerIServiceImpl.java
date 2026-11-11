package com.x.ai.db.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.x.ai.db.entity.AgentMcpServer;
import com.x.ai.db.mapper.AgentMcpServerMapper;
import com.x.ai.db.service.AgentMcpServerIService;
import org.springframework.stereotype.Service;

@Service
public class AgentMcpServerIServiceImpl extends ServiceImpl<AgentMcpServerMapper, AgentMcpServer> implements AgentMcpServerIService {
}
