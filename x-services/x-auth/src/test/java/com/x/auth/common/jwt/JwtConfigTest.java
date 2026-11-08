package com.x.auth.common.jwt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtConfigTest {
    @Test
    void rejectsMissingWeakAndUnresolvedDeploymentSecrets() {
        JwtConfig config = new JwtConfig();
        for (String secret : new String[] {null, "", "short", "${UNRESOLVED_JWT_SECRET_PLACEHOLDER}"}) {
            assertThatIllegalArgumentException().isThrownBy(() -> config.setSign(secret));
        }
    }

    @Test
    void acceptsTheConfiguredDeploymentSecret() {
        String previous = JwtConfig.sign;
        try {
            new JwtConfig().setSign("test-only-jwt-signing-secret-32-chars");
            assertThat(JwtConfig.sign).isEqualTo("test-only-jwt-signing-secret-32-chars");
        } finally {
            JwtConfig.sign = previous;
        }
    }
}
