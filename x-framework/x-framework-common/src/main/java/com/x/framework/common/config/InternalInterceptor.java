package com.x.framework.common.config;

import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.utils.DateUtil;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import com.x.framework.common.auth.InternalRequestSigner;
import org.springframework.beans.factory.annotation.Value;

/**
 * @author : xuemingqi
 * @since : 2023/1/14 15:49
 */
public class InternalInterceptor implements RequestInterceptor {
    private final InternalRequestSigner signer;

    public InternalInterceptor(@Value("${x.security.internal-secret}") String secret) {
        this.signer = new InternalRequestSigner(secret);
    }
    @Override
    public void apply(RequestTemplate template) {
        //时间戳
        String timestamp = DateUtil.getTimestamp().toString();
        //url
        String url = template.path();
        String token = signer.sign(url, timestamp);

        //set header
        template.header(CommonConstant.INTERNAL_TOKEN, token);
        template.header(CommonConstant.INTERNAL_TIMESTAMP, timestamp);
    }
}
