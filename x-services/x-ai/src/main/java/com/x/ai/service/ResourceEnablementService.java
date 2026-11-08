package com.x.ai.service;

import com.x.ai.common.model.ResourceEnablement;

public interface ResourceEnablementService {

    /**
     * 读取指定用户的扩展启用快照，未设置的资源默认启用。
     */
    ResourceEnablement get(String userId);

    /**
     * 保存单项用户偏好；调用方必须先校验该资源对用户可见。
     */
    void setEnabled(String userId, String resourceKey, boolean enabled);
}
