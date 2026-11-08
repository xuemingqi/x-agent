package com.x.ai.db.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.x.ai.db.entity.AgentConversationMessage;
import com.x.ai.db.mapper.AgentConversationMessageMapper;
import com.x.ai.db.service.AgentConversationMessageIService;
import org.springframework.stereotype.Service;

/**
 * @author : xuemingqi
 * @date : 2026/7/17 17:35
 */
@Service
public class AgentConversationMessageIServiceImpl extends ServiceImpl<AgentConversationMessageMapper, AgentConversationMessage> implements AgentConversationMessageIService {
}
