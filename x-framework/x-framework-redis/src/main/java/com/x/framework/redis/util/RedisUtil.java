package com.x.framework.redis.util;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Redis operations used by authentication, public-resource versioning and task interruption. */
public class RedisUtil {

    private final RedissonClient redissonClient;

    public RedisUtil(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public <T> T get(String key) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        return bucket.get();
    }

    public <T> void set(String key, T value, long timeout, TimeUnit timeUnit) {
        redissonClient.getBucket(key).set(value, Duration.ofMillis(timeUnit.toMillis(timeout)));
    }

    public boolean del(String key) {
        return redissonClient.getBucket(key).delete();
    }

    public long incr(String key, long delta) {
        return redissonClient.getAtomicLong(key).addAndGet(delta);
    }

    public long publish(String topic, Object message) {
        return redissonClient.getTopic(topic).publish(message);
    }

    public <T> int subscribe(String topic, Class<T> messageClass, MessageListener<? extends T> listener) {
        return redissonClient.getTopic(topic).addListener(messageClass, listener);
    }

    public void unsubscribe(String topic, int listenerId) {
        redissonClient.getTopic(topic).removeListener(listenerId);
    }
}
