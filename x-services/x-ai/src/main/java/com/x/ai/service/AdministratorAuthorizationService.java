package com.x.ai.service;

import com.x.ai.common.domain.AdministrationStatusDo;

public interface AdministratorAuthorizationService {

    /**
     * 查询可信用户是否为 系统管理员。
     *
     * @param userId 可信用户 ID
     * @return 是否为管理员
     */
    boolean isAdministrator(String userId);

    /**
     * 要求可信用户具备管理员权限。
     *
     * @param userId 可信用户 ID
     */
    void requireAdministrator(String userId);

    /**
     * 查询当前登录用户的角色状态。
     *
     * @return 当前角色状态
     */
    AdministrationStatusDo currentStatus();
}
