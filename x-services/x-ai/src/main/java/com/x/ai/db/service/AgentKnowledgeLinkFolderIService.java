package com.x.ai.db.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.x.ai.db.entity.AgentKnowledgeLinkFolder;
import com.x.ai.db.enums.AgentKnowledgeLinkScope;

public interface AgentKnowledgeLinkFolderIService extends IService<AgentKnowledgeLinkFolder> {

    /**
     * 在调用方事务内按作用域和归属用户查询并锁定文件夹。
     */
    AgentKnowledgeLinkFolder findForUpdate(Long id, AgentKnowledgeLinkScope scope, String ownerUserId);
}
