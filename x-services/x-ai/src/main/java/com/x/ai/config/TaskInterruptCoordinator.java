package com.x.ai.config;

import com.x.ai.common.enums.InterruptDispatch;
import com.x.ai.common.model.InterruptCommand;
import com.x.ai.properties.AgentProperties;
import com.x.ai.service.ActiveTaskRegistryService;
import com.x.framework.redis.util.RedisUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskInterruptCoordinator {

    private final ActiveTaskRegistryService activeTaskRegistryService;

    private final RedisUtil redisUtil;

    private final AgentProperties agentProperties;

    private int listenerId;

    /**
     * 订阅跨实例中断主题，并将消息限制在其声明的任务所有者范围内处理。
     */
    @PostConstruct
    void subscribe() {
        listenerId = redisUtil.subscribe(agentProperties.getInterrupt().getTopic(), InterruptCommand.class,
                (channel, command) -> activeTaskRegistryService.interrupt(command.getTaskId(), command.getUserId()));
    }

    /**
     * 优先中断本实例任务；本地不存在时向其他实例广播。
     *
     * @param taskId 任务标识
     * @param userId 任务所有者标识
     * @return 本地或分布式中断分派方式
     */
    public InterruptDispatch interrupt(String taskId, String userId) {
        if (activeTaskRegistryService.interrupt(taskId, userId)) {
            return InterruptDispatch.LOCAL;
        }
        redisUtil.publish(agentProperties.getInterrupt().getTopic(), new InterruptCommand(taskId, userId));
        return InterruptDispatch.DISTRIBUTED;
    }

    /**
     * 组件销毁时移除中断主题监听器。
     */
    @PreDestroy
    void unsubscribe() {
        if (listenerId != 0) {
            redisUtil.unsubscribe(agentProperties.getInterrupt().getTopic(), listenerId);
        }
    }
}
