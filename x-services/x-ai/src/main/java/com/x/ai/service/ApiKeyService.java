package com.x.ai.service;

import com.x.ai.common.domain.ApiKeyDo;
import com.x.framework.common.auth.ApiKeyAuthenticationClient;

/**
 * API Key 签发与认证服务。
 *
 * @author xuemingqi
 */
public interface ApiKeyService extends ApiKeyAuthenticationClient {

    /**
     * 为当前登录用户签发或轮换 API Key。
     *
     * @return 新 API Key，明文仅返回本次
     */
    ApiKeyDo issue();
}
