package com.x.ai.common.domain;

import com.x.ai.common.enums.UserRole;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AdministrationStatusDo {

    /**
     * 当前用户角色。
     */
    private UserRole role;

    /**
     * 当前用户是否为管理员。
     */
    private boolean administrator;
}
