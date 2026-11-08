package com.x.ai.common;

import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.framework.common.utils.ServerUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserContextTest {

    @AfterEach
    void tearDown() {
        UserThreadLocalUtil.clear();
    }

    @Test
    void currentUserIdAsLongConvertsAuthenticatedStringUserId() {
        UserThreadLocalUtil.set(UserInfo.builder()
                .userId("123456789")
                .mobile("13800000000")
                .build());

        assertThat(ServerUtil.getAuthenticatedUserId()).isEqualTo(123456789L);
    }

    @Test
    void currentUserIdAsStringUsesAuthenticatedUserId() {
        UserThreadLocalUtil.set(UserInfo.builder()
                .userId("42")
                .mobile("13800000000")
                .build());

        assertThat(UserThreadLocalUtil.getUserId()).isEqualTo("42");
    }
}
