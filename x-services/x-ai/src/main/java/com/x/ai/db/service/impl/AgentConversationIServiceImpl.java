package com.x.ai.db.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.x.ai.db.entity.AgentConversation;
import com.x.ai.db.mapper.AgentConversationMapper;
import com.x.ai.db.service.AgentConversationIService;
import org.springframework.stereotype.Service;

/**
 * @author : xuemingqi
 * @date : 2026/7/17 17:33
 */
@Service
public class AgentConversationIServiceImpl extends ServiceImpl<AgentConversationMapper, AgentConversation> implements AgentConversationIService {
}
