package com.x.framework.redis.conf;

import cn.hutool.core.util.StrUtil;
import com.x.framework.redis.property.RedissonClusterProperty;
import com.x.framework.redis.property.RedissonMasterSlaveProperty;
import com.x.framework.redis.property.RedissonSingleProperty;
import com.x.framework.redis.type.RedissonType;
import com.x.framework.redis.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJackson3Codec;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.redisson.connection.balancer.RoundRobinLoadBalancer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * @author : xuemingqi
 * @since : 2023/1/7 16:38
 */
@Slf4j
@Configuration
@ConditionalOnClass(RedissonClient.class)
@RequiredArgsConstructor(onConstructor = @__({@Autowired}))
public class RedissonConfig {

    private final RedissonSingleProperty singleProperty;

    private final RedissonMasterSlaveProperty masterSlaveProperty;

    private final RedissonClusterProperty clusterProperty;

    private final RedissonType redissonType;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.setCodec(new JsonJackson3Codec());
        switch (redissonType.getType()) {
            case SINGLE -> createSingleConfig(config);
            case MASTER_SLAVE -> createMasterSlaveConfig(config);
            case CLUSTER -> createClusterConfig(config);
        }
        return Redisson.create(config);
    }

    /**
     * redisUtil bean
     */
    @Bean
    public RedisUtil redisUtil(RedissonClient redissonClient) {
        return new RedisUtil(redissonClient);
    }

    private void createSingleConfig(Config config) {
        config.useSingleServer()
                .setAddress(singleProperty.getAddress())
                .setConnectionPoolSize(singleProperty.getConnectionPoolSize())
                .setIdleConnectionTimeout(singleProperty.getIdleConnectionTimeout())
                .setConnectTimeout(singleProperty.getConnectTimeout())
                .setConnectionMinimumIdleSize(singleProperty.getConnectionMinimumIdleSize())
                .setTimeout(singleProperty.getTimeout());
        if (StrUtil.isNotBlank(singleProperty.getPassword())) {
            config.setPassword(singleProperty.getPassword());
        }
    }

    private void createMasterSlaveConfig(Config config) {
        config.useMasterSlaveServers()
                .setConnectTimeout(masterSlaveProperty.getConnectTimeout())
                .setIdleConnectionTimeout(masterSlaveProperty.getIdleConnectionTimeout())
                .setMasterConnectionPoolSize(masterSlaveProperty.getMasterConnectionPoolSize())
                .setMasterConnectionMinimumIdleSize(masterSlaveProperty.getMasterConnectionMinimumIdleSize())
                .setSlaveConnectionPoolSize(masterSlaveProperty.getSlaveConnectionPoolSize())
                .setSlaveConnectionMinimumIdleSize(masterSlaveProperty.getSlaveConnectionMinimumIdleSize())
                .setReadMode(ReadMode.MASTER)
                .setMasterAddress(masterSlaveProperty.getMasterAddress())
                .setSlaveAddresses(masterSlaveProperty.getSlaveAddress());
        if (StrUtil.isNotBlank(masterSlaveProperty.getPassword())) {
            config.setPassword(masterSlaveProperty.getPassword());
        }
    }

    private void createClusterConfig(Config config) {
        config.useClusterServers()
                .addNodeAddress(clusterProperty.getNodeAddresses())
                .setConnectTimeout(clusterProperty.getConnectTimeout())
                .setIdleConnectionTimeout(clusterProperty.getIdleConnectionTimeout())
                .setTimeout(clusterProperty.getTimeout())
                .setMasterConnectionMinimumIdleSize(clusterProperty.getMasterConnectionMinimumIdleSize())
                .setMasterConnectionPoolSize(clusterProperty.getMasterConnectionPoolSize())
                .setSlaveConnectionMinimumIdleSize(clusterProperty.getMasterConnectionMinimumIdleSize())
                .setSlaveConnectionPoolSize(clusterProperty.getSlaveConnectionPoolSize())
                .setRetryAttempts(clusterProperty.getRetryAttempts())
                .setLoadBalancer(new RoundRobinLoadBalancer())
                .setRetryDelay(attempt -> Duration.ofSeconds(clusterProperty.getRetryAttempts()))
                .setSubscriptionsPerConnection(clusterProperty.getSubscriptionsPerConnection())
                .setScanInterval(clusterProperty.getScanInterval())
                .setReadMode(ReadMode.MASTER_SLAVE);
        if (StrUtil.isNotBlank(clusterProperty.getPassword())) {
            config.setPassword(clusterProperty.getPassword());
        }
    }
}
