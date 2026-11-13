package com.x.ai.db.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.x.ai.db.entity.AgentAppData;

public interface AgentAppDataIService extends IService<AgentAppData> {

    /**
     * 依赖用户、应用和键的唯一约束原子新增或替换 JSON 值。
     */
    void upsert(AgentAppData data);
}
