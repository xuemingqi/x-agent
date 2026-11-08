package com.x.framework.redis.autoconfigure;

import com.x.framework.redis.conf.JedisConfig;
import com.x.framework.redis.conf.RedissonConfig;
import com.x.framework.redis.property.RedissonClusterProperty;
import com.x.framework.redis.property.RedissonMasterSlaveProperty;
import com.x.framework.redis.property.RedissonSingleProperty;
import com.x.framework.redis.type.RedissonType;
import com.x.framework.redis.type.RedissonTypeConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
        JedisConfig.class,
        RedissonClusterProperty.class,
        RedissonConfig.class,
        RedissonMasterSlaveProperty.class,
        RedissonSingleProperty.class,
        RedissonType.class,
        RedissonTypeConverter.class
})
public class XFrameworkRedisAutoConfiguration {
}
