package com.x.ai.service.impl;


import com.x.ai.common.constants.AgentConstant;
import com.x.ai.service.AgentScopeStoreService;
import io.agentscope.extensions.redis.RedisDistributedStore;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.sandbox.snapshot.RemoteSnapshotClient;
import io.agentscope.harness.agent.sandbox.snapshot.RemoteSnapshotSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import redis.clients.jedis.UnifiedJedis;

/**
 * @author : xuemingqi
 * @date : 2026/7/21 11:31
 */
@Service
@RequiredArgsConstructor
public class AgentScopeStoreServiceImpl implements AgentScopeStoreService {

    private final UnifiedJedis jedis;

    private final RemoteSnapshotClient remoteSnapshotClient;


    @Override
    public DistributedStore createRuntimeStore() {
        RedisDistributedStore redisStore = RedisDistributedStore.fromJedis(jedis, AgentConstant.AGENT_SCOPE_REDIS_KEY_PREFIX);
        return DistributedStore.builder()
                .agentStateStore(redisStore.agentStateStore())
                .baseStore(redisStore.baseStore())
                .sandboxSnapshotSpec(new RemoteSnapshotSpec(remoteSnapshotClient))
                .sandboxExecutionGuard(redisStore.sandboxExecutionGuard())
                .build();
    }
}
