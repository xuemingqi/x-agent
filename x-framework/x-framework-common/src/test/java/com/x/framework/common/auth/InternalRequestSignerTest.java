package com.x.framework.common.auth;

import com.x.framework.common.config.InternalInterceptor;
import com.x.framework.common.constants.CommonConstant;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class InternalRequestSignerTest {
    private static final String SECRET = "test-only-internal-signing-secret-32-chars";
    private final InternalRequestSigner signer = new InternalRequestSigner(SECRET);

    @Test
    void feignHeadersVerifyAtTheReceiver() {
        RequestTemplate request = new RequestTemplate();
        request.uri("/contact/user/auth");
        new InternalInterceptor(SECRET).apply(request);
        String timestamp = request.headers().get(CommonConstant.INTERNAL_TIMESTAMP).iterator().next();
        String signature = request.headers().get(CommonConstant.INTERNAL_TOKEN).iterator().next();
        assertThat(signer.verify(request.path(), timestamp, signature, System.currentTimeMillis())).isTrue();
        assertThat(signer.verify("/contact/user/create", timestamp, signature, System.currentTimeMillis())).isFalse();
        assertThat(new InternalRequestSigner(SECRET + "other").verify(request.path(), timestamp, signature,
                System.currentTimeMillis())).isFalse();
    }

    @Test
    void rejectsExpiredMalformedAndMissingSignatures() {
        long now = System.currentTimeMillis();
        for (String timestamp : new String[] {null, "invalid", "0", Long.toString(now - 120001),
                Long.toString(now + 120001), Long.toString(Long.MIN_VALUE), Long.toString(Long.MAX_VALUE)}) {
            assertThat(signer.verify("/auth/check", timestamp, signer.sign("/auth/check", timestamp), now)).isFalse();
        }
        assertThat(signer.verify("/auth/check", Long.toString(now), null, now)).isFalse();
    }

    @Test
    void requiresADeploymentSecret() {
        for (String secret : new String[] {null, "", "short", "${UNRESOLVED_INTERNAL_SECRET_PLACEHOLDER}"}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new InternalRequestSigner(secret));
        }
    }
}
