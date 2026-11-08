package com.x.ai.service.impl;

import com.x.ai.common.model.Registration;
import com.x.ai.service.ActiveTaskRegistryService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ActiveTaskRegistryServiceImpl implements ActiveTaskRegistryService {

    private final ConcurrentMap<String, ActiveTask> tasks = new ConcurrentHashMap<>();


    @Override
    public Registration register(String taskId, String userId, Runnable interruptAction) {
        ActiveTask task = new ActiveTask(userId, interruptAction);
        if (tasks.putIfAbsent(taskId, task) != null) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        return new Registration(() -> unregister(taskId, task));
    }

    @Override
    public boolean interrupt(String taskId, String requestingUserId) {
        ActiveTask task = tasks.get(taskId);
        if (task == null) {
            return false;
        }
        if (!Objects.equals(task.userId, requestingUserId)) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        if (task.interrupted.compareAndSet(false, true)) {
            task.interruptAction.run();
        }
        return true;
    }

    private void unregister(String taskId, ActiveTask task) {
        tasks.remove(taskId, task);
    }

    private static final class ActiveTask {

        private final String userId;

        private final Runnable interruptAction;

        private final AtomicBoolean interrupted = new AtomicBoolean();


        private ActiveTask(String userId, Runnable interruptAction) {
            this.userId = Objects.requireNonNull(userId, "userId");
            this.interruptAction = Objects.requireNonNull(interruptAction, "interruptAction");
        }
    }
}
