package com.x.ai.db.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.x.ai.db.entity.AgentApp;

public interface AgentAppIService extends IService<AgentApp> {

    /**
     * 在调用方事务内锁定当前用户的应用，串行化版本发布、回滚和删除。
     */
    AgentApp findForUpdate(String ownerUserId, String id);
}
