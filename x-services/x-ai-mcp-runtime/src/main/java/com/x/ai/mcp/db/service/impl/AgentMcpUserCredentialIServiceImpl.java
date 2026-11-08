package com.x.ai.mcp.db.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.x.ai.mcp.db.entity.AgentMcpUserCredential;
import com.x.ai.mcp.db.mapper.AgentMcpUserCredentialMapper;
import com.x.ai.mcp.db.service.AgentMcpUserCredentialIService;
import org.springframework.stereotype.Service;

@Service
public class AgentMcpUserCredentialIServiceImpl extends ServiceImpl<AgentMcpUserCredentialMapper, AgentMcpUserCredential> implements AgentMcpUserCredentialIService {
}
