package com.x.ai.db.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.x.ai.db.entity.AgentKnowledgeLink;
import com.x.ai.db.mapper.AgentKnowledgeLinkMapper;
import com.x.ai.db.service.AgentKnowledgeLinkIService;
import org.springframework.stereotype.Service;

@Service
public class AgentKnowledgeLinkIServiceImpl extends ServiceImpl<AgentKnowledgeLinkMapper, AgentKnowledgeLink> implements AgentKnowledgeLinkIService {
}
