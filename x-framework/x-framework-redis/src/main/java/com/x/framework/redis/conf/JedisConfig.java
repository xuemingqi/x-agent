package com.x.framework.redis.conf;

import cn.hutool.core.util.StrUtil;
import com.x.framework.redis.property.RedissonClusterProperty;
import com.x.framework.redis.property.RedissonMasterSlaveProperty;
import com.x.framework.redis.property.RedissonSingleProperty;
import com.x.framework.redis.type.RedissonType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Connection;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnClass(UnifiedJedis.class)
@RequiredArgsConstructor
public class JedisConfig {

    private final RedissonSingleProperty singleProperty;

    private final RedissonMasterSlaveProperty masterSlaveProperty;

    private final RedissonClusterProperty clusterProperty;

    private final RedissonType redissonType;

    @Bean(destroyMethod = "close")
    public UnifiedJedis jedis() {
        return switch (redissonType.getType()) {
            case SINGLE -> createSingleJedis();
            case MASTER_SLAVE -> createMasterSlaveJedis();
            case CLUSTER -> createClusterJedis();
        };
    }

    private UnifiedJedis createSingleJedis() {
        return createPooledJedis(
                singleProperty.getAddress(),
                singleProperty.getPassword(),
                singleProperty.getConnectTimeout(),
                singleProperty.getTimeout(),
                singleProperty.getConnectionPoolSize(),
                singleProperty.getConnectionMinimumIdleSize(),
                singleProperty.getIdleConnectionTimeout());
    }

    private UnifiedJedis createMasterSlaveJedis() {
        return createPooledJedis(
                masterSlaveProperty.getMasterAddress(),
                masterSlaveProperty.getPassword(),
                masterSlaveProperty.getConnectTimeout(),
                masterSlaveProperty.getTimeout(),
                masterSlaveProperty.getMasterConnectionPoolSize(),
                masterSlaveProperty.getMasterConnectionMinimumIdleSize(),
                masterSlaveProperty.getIdleConnectionTimeout());
    }

    private UnifiedJedis createClusterJedis() {
        String[] addresses = clusterProperty.getNodeAddresses();
        Set<HostAndPort> nodes = Arrays.stream(addresses)
                .map(this::toHostAndPort)
                .collect(Collectors.toSet());
        JedisClientConfig clientConfig = createClientConfig(
                URI.create(addresses[0]),
                clusterProperty.getPassword(),
                clusterProperty.getConnectTimeout(),
                clusterProperty.getTimeout());
        GenericObjectPoolConfig<Connection> poolConfig = createPoolConfig(
                clusterProperty.getMasterConnectionPoolSize(),
                clusterProperty.getMasterConnectionMinimumIdleSize(),
                clusterProperty.getIdleConnectionTimeout());
        return new JedisCluster(nodes, clientConfig, clusterProperty.getRetryAttempts(), poolConfig);
    }

    private JedisPooled createPooledJedis(String address, String password, int connectTimeout,
            int timeout, int poolSize, int minimumIdleSize, int idleConnectionTimeout) {
        URI uri = URI.create(address);
        JedisClientConfig clientConfig = createClientConfig(uri, password, connectTimeout, timeout);
        GenericObjectPoolConfig<Connection> poolConfig = createPoolConfig(
                poolSize,
                minimumIdleSize,
                idleConnectionTimeout);
        return new JedisPooled(poolConfig, toHostAndPort(uri), clientConfig);
    }

    private JedisClientConfig createClientConfig(URI address, String password, int connectTimeout, int timeout) {
        DefaultJedisClientConfig.Builder clientConfig = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(connectTimeout)
                .socketTimeoutMillis(timeout)
                .ssl("rediss".equalsIgnoreCase(address.getScheme()));
        if (StrUtil.isNotBlank(password)) {
            clientConfig.password(password);
        }
        return clientConfig.build();
    }

    private GenericObjectPoolConfig<Connection> createPoolConfig(int poolSize, int minimumIdleSize, int idleConnectionTimeout) {
        GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(poolSize);
        poolConfig.setMaxIdle(poolSize);
        poolConfig.setMinIdle(minimumIdleSize);
        Duration idleTimeout = Duration.ofMillis(idleConnectionTimeout);
        poolConfig.setMinEvictableIdleDuration(idleTimeout);
        poolConfig.setTimeBetweenEvictionRuns(idleTimeout);
        return poolConfig;
    }

    private HostAndPort toHostAndPort(String address) {
        return toHostAndPort(URI.create(address));
    }

    private HostAndPort toHostAndPort(URI address) {
        return new HostAndPort(address.getHost(), address.getPort());
    }
}
