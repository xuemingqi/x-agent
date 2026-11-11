package com.x.ai.db.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.x.ai.db.entity.AgentKnowledgeLinkFolder;

public interface AgentKnowledgeLinkFolderIService extends IService<AgentKnowledgeLinkFolder> {

    /**
     * 在调用方事务内按归属用户查询并锁定文件夹。
     */
    AgentKnowledgeLinkFolder findForUpdate(Long id, String ownerUserId);
}
