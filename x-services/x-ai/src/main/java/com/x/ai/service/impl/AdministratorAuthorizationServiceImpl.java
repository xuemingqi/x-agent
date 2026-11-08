package com.x.ai.service.impl;

import com.x.ai.common.domain.AdministrationStatusDo;
import com.x.ai.common.enums.UserRole;
import com.x.ai.service.AdministratorAuthorizationService;
import com.x.api.contact.api.UserApi;
import com.x.api.contact.domain.UserDo;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.utils.StringUtil;
import com.x.framework.common.utils.UserThreadLocalUtil;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdministratorAuthorizationServiceImpl implements AdministratorAuthorizationService {

    private final UserApi userApi;

    @Override
    public boolean isAdministrator(String userId) {
        if (StringUtil.isBlank(userId)) {
            return false;
        }
        BaseResult<UserDo> result;
        try {
            result = userApi.getUserById(StringUtil.parseLongId(userId));
        } catch (FeignException exception) {
            throw new XException(ResponseCodeEnum.ADMIN_AUTHORIZATION_UNAVAILABLE);
        }
        if (result == null || result.getCode() != ResponseCodeEnum.SUCCESS.getCode() || result.getData() == null) {
            throw new XException(ResponseCodeEnum.ADMIN_AUTHORIZATION_UNAVAILABLE);
        }
        return Boolean.TRUE.equals(result.getData().getIsAdmin());
    }

    @Override
    public void requireAdministrator(String userId) {
        if (!isAdministrator(userId)) {
            throw new XException(ResponseCodeEnum.ADMIN_PERMISSION_REQUIRED);
        }
    }

    @Override
    public AdministrationStatusDo currentStatus() {
        boolean administrator = isAdministrator(UserThreadLocalUtil.getUserId());
        return new AdministrationStatusDo()
                .setRole(administrator ? UserRole.ADMIN : UserRole.USER)
                .setAdministrator(administrator);
    }
}
