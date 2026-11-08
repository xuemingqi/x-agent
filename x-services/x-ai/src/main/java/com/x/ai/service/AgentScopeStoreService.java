package com.x.ai.service;

import io.agentscope.harness.agent.DistributedStore;

public interface AgentScopeStoreService {

    /**
     * Redis 保存 Agent 状态、轻量 KV 和沙箱锁，MinIO 保存 Sandbox 快照。
     *
     * @return DistributedStore
     */
    DistributedStore createRuntimeStore();
}
