package com.x.framework.common.auth;

import com.x.framework.common.dto.UserInfo;

/**
 * API Key 鉴权客户端。
 *
 * @author xuemingqi
 */
public interface ApiKeyAuthenticationClient {

    /**
     * 校验 API Key 并返回其所属用户。
     *
     * @param apiKey API Key 明文
     * @return API Key 所属用户
     */
    UserInfo authenticateApiKey(String apiKey);
}
