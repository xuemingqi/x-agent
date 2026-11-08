package com.x.ai.scheduler.task;

import com.x.ai.scheduler.service.QuartzAgentSchedulerService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import io.agentscope.core.message.Msg;
import reactor.core.publisher.Mono;

public class AgentScheduleTask implements io.agentscope.extensions.scheduler.ScheduleAgentTask<Msg> {

    private final long scheduleTaskId;

    private final QuartzAgentSchedulerService quartzAgentSchedulerService;

    public AgentScheduleTask(long scheduleTaskId, QuartzAgentSchedulerService quartzAgentSchedulerService) {
        this.scheduleTaskId = scheduleTaskId;
        this.quartzAgentSchedulerService = quartzAgentSchedulerService;
    }

    @Override
    public String getId() {
        return String.valueOf(scheduleTaskId);
    }

    @Override
    public String getName() {
        return String.valueOf(scheduleTaskId);
    }

    /**
     * 通过 Quartz 立即触发持久化任务；触发失败时返回错误信号。
     */
    @Override
    public Mono<Msg> run(Msg... msgs) {
        return Mono.fromRunnable(() -> {
                    if (!quartzAgentSchedulerService.triggerNow(scheduleTaskId)) {
                        throw new XException(ResponseCodeEnum.AGENT_EXECUTION_ERROR);
                    }
                })
                .then(Mono.empty());
    }

    /**
     * 删除该任务对应的 Quartz Job。
     */
    @Override
    public void cancel() {
        quartzAgentSchedulerService.cancel(scheduleTaskId);
    }
}
