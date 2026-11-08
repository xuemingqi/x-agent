package com.x.ai.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.db.entity.AgentDisabledResource;
import com.x.ai.db.service.AgentDisabledResourceIService;
import com.x.ai.service.ResourceEnablementService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceEnablementServiceImpl implements ResourceEnablementService {

    private final AgentDisabledResourceIService disabledResourceIService;


    @Override
    public ResourceEnablement get(String userId) {
        return new ResourceEnablement(disabledResourceIService.lambdaQuery()
                .eq(AgentDisabledResource::getUserId, userId)
                .list().stream().map(AgentDisabledResource::getResourceKey).collect(Collectors.toSet()));
    }

    @Override
    public void setEnabled(String userId, String resourceKey, boolean enabled) {
        String id = DigestUtil.sha256Hex(userId + "\n" + resourceKey);
        if (enabled) {
            disabledResourceIService.removeById(id);
        } else {
            if (!disabledResourceIService.saveOrUpdate(new AgentDisabledResource()
                    .setId(id).setUserId(userId).setResourceKey(resourceKey))) {
                throw new XException(ResponseCodeEnum.AGENT_WORKSPACE_STORAGE_ERROR);
            }
        }
    }
}
