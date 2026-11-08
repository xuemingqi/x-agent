package com.x.framework.common.utils;

import com.x.framework.common.dto.UserInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserThreadLocalUtilTest {

    @AfterEach
    void clearUserContext() {
        UserThreadLocalUtil.clear();
    }

    @Test
    void getsUserIdFromCurrentUserContext() {
        UserThreadLocalUtil.set(UserInfo.builder().userId("sso-user").build());

        assertThat(UserThreadLocalUtil.getUserId()).isEqualTo("sso-user");
    }
}
