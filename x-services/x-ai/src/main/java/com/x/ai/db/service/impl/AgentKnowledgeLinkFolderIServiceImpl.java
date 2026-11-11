package com.x.ai.db.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.x.ai.db.entity.AgentKnowledgeLinkFolder;
import com.x.ai.db.mapper.AgentKnowledgeLinkFolderMapper;
import com.x.ai.db.service.AgentKnowledgeLinkFolderIService;
import org.springframework.stereotype.Service;

@Service
public class AgentKnowledgeLinkFolderIServiceImpl extends ServiceImpl<AgentKnowledgeLinkFolderMapper, AgentKnowledgeLinkFolder> implements AgentKnowledgeLinkFolderIService {

    @Override
    public AgentKnowledgeLinkFolder findForUpdate(Long id, String ownerUserId) {
        return lambdaQuery()
                .eq(AgentKnowledgeLinkFolder::getId, id)
                .eq(AgentKnowledgeLinkFolder::getOwnerUserId, ownerUserId)
                .last("FOR UPDATE")
                .one();
    }
}
