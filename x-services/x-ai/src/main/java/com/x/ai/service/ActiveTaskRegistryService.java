package com.x.ai.service;

import com.x.ai.common.model.Registration;

public interface ActiveTaskRegistryService {

    /**
     * 注册运行中的任务及其中断动作；任务标识重复时拒绝覆盖。
     *
     * @return 必须在任务终止时关闭的注册句柄
     */
    Registration register(String taskId, String userId, Runnable interruptAction);

    /**
     * 仅允许任务所有者幂等触发一次中断动作。
     *
     * @param taskId           任务标识
     * @param requestingUserId 请求中断的用户标识
     * @return 任务是否存在并已受理中断
     */
    boolean interrupt(String taskId, String requestingUserId);
}
