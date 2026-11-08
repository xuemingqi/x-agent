package com.x.ai.service.impl;

import com.x.ai.common.domain.ApiKeyDo;
import com.x.ai.db.entity.AgentApiKey;
import com.x.ai.db.service.AgentApiKeyIService;
import com.x.ai.service.ApiKeyService;
import com.x.framework.common.annotation.LogIgnore;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.IdUtil;
import com.x.framework.common.utils.ServerUtil;
import com.x.framework.common.utils.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final int API_KEY_RANDOM_BYTES = 32;

    private final AgentApiKeyIService agentApiKeyIService;

    @Override
    @Transactional
    public ApiKeyDo issue() {
        UserInfo userInfo = ServerUtil.getAuthenticatedUser();
        String apiKey = CommonConstant.API_KEY_PREFIX + IdUtil.secureRandomUrlSafe(API_KEY_RANDOM_BYTES);
        agentApiKeyIService.saveOrUpdate(new AgentApiKey()
                .setUserId(userInfo.getUserId())
                .setKeyHash(StringUtil.sha256Hex(apiKey)));
        return new ApiKeyDo().setApiKey(apiKey);
    }

    @Override
    @LogIgnore
    public UserInfo authenticateApiKey(String apiKey) {
        if (StringUtil.isBlank(apiKey) || !apiKey.startsWith(CommonConstant.API_KEY_PREFIX)) {
            throw new XException(ResponseCodeEnum.API_KEY_INVALID);
        }
        AgentApiKey storedKey = agentApiKeyIService.lambdaQuery()
                .eq(AgentApiKey::getKeyHash, StringUtil.sha256Hex(apiKey))
                .one();
        if (storedKey == null) {
            throw new XException(ResponseCodeEnum.API_KEY_INVALID);
        }
        return UserInfo.builder().userId(storedKey.getUserId()).build();
    }
}
