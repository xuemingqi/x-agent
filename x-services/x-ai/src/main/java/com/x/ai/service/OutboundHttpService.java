package com.x.ai.service;

import com.x.ai.common.domain.HttpExecutionDo;
import com.x.ai.common.model.HttpRequestSpec;
import reactor.core.publisher.Mono;

public interface OutboundHttpService {

    /**
     * 执行一次 HTTP 请求并测量完整响应耗时；取消订阅会终止底层网络请求。
     */
    Mono<HttpExecutionDo> execute(HttpRequestSpec request);
}
