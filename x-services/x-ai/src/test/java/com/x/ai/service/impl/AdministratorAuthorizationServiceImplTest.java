package com.x.ai.service.impl;

import com.x.ai.common.enums.UserRole;
import com.x.api.contact.api.UserApi;
import com.x.api.contact.domain.UserDo;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.response.ResultUtil;
import com.x.framework.common.utils.UserThreadLocalUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdministratorAuthorizationServiceImplTest {

    private final UserApi userApi = mock(UserApi.class);

    private final AdministratorAuthorizationServiceImpl service = new AdministratorAuthorizationServiceImpl(userApi);

    @AfterEach
    void clearIdentity() {
        UserThreadLocalUtil.clear();
    }

    @Test
    void eachUserHasAnIndependentAdministratorFlag() {
        when(userApi.getUserById(1L)).thenReturn(ResultUtil.buildResultSuccess(
                UserDo.builder().id(1L).isAdmin(true).build()));
        when(userApi.getUserById(2L)).thenReturn(ResultUtil.buildResultSuccess(
                UserDo.builder().id(2L).isAdmin(false).build()));

        assertThat(service.isAdministrator("1")).isTrue();
        assertThat(service.isAdministrator("2")).isFalse();
        assertThatThrownBy(() -> service.requireAdministrator("2"))
                .isInstanceOfSatisfying(XException.class,
                        error -> assertThat(error.getResponseCodeEnum()).isEqualTo(ResponseCodeEnum.ADMIN_PERMISSION_REQUIRED));
    }

    @Test
    void revocationTakesEffectWithoutIssuingAnotherLoginToken() {
        when(userApi.getUserById(1L)).thenReturn(
                ResultUtil.buildResultSuccess(UserDo.builder().id(1L).isAdmin(true).build()),
                ResultUtil.buildResultSuccess(UserDo.builder().id(1L).isAdmin(false).build()));

        assertThat(service.isAdministrator("1")).isTrue();
        assertThat(service.isAdministrator("1")).isFalse();
    }

    @Test
    void missingAdministratorFlagNeverGrantsPublicWritePermission() {
        when(userApi.getUserById(1L)).thenReturn(ResultUtil.buildResultSuccess(UserDo.builder().id(1L).build()));

        assertThat(service.isAdministrator("1")).isFalse();
    }

    @Test
    void failedUserServiceResponseNeverGrantsPermission() {
        when(userApi.getUserById(1L)).thenReturn(ResultUtil.buildGeneralResultError());

        assertThatThrownBy(() -> service.requireAdministrator("1"))
                .isInstanceOfSatisfying(XException.class,
                        error -> assertThat(error.getResponseCodeEnum()).isEqualTo(ResponseCodeEnum.ADMIN_AUTHORIZATION_UNAVAILABLE));
    }

    @Test
    void statusUsesTheAuthenticatedAccount() {
        UserThreadLocalUtil.set(UserInfo.builder().userId("2").build());
        when(userApi.getUserById(2L)).thenReturn(ResultUtil.buildResultSuccess(
                UserDo.builder().id(2L).isAdmin(false).build()));

        var status = service.currentStatus();

        assertThat(status.getRole()).isEqualTo(UserRole.USER);
        assertThat(status.isAdministrator()).isFalse();
    }
}
