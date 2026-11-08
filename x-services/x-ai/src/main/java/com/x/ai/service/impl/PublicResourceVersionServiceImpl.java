package com.x.ai.service.impl;

import com.x.framework.redis.util.RedisUtil;
import com.x.ai.service.PublicResourceVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublicResourceVersionServiceImpl implements PublicResourceVersionService {

    private static final String PUBLIC_RESOURCE_VERSION_KEY = "xagent:public-resource:version";

    private final RedisUtil redisUtil;

    @Override
    public long currentVersion() {
        return redisUtil.incr(PUBLIC_RESOURCE_VERSION_KEY, 0L);
    }

    @Override
    public long advanceVersion() {
        return redisUtil.incr(PUBLIC_RESOURCE_VERSION_KEY, 1L);
    }
}
