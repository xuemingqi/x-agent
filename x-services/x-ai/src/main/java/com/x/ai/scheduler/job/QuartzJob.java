package com.x.ai.scheduler.job;

import com.x.ai.common.constants.AgentConstant;
import com.x.ai.common.model.QuartzFireMetadata;
import com.x.ai.service.AgentScheduleManagementService;
import lombok.RequiredArgsConstructor;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

@DisallowConcurrentExecution
@RequiredArgsConstructor
public class QuartzJob implements InterruptableJob {

    private volatile boolean interrupted;

    private final AgentScheduleManagementService agentScheduleManagementService;


    /**
     * 将 Quartz 触发元数据交给定时任务服务，并在结束后续接固定延迟任务。
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        if (interrupted) {
            return;
        }
        String rawTaskId = context.getMergedJobDataMap().getString(AgentConstant.SCHEDULE_TASK_ID);
        String rawExecutionId = context.getMergedJobDataMap().getString(AgentConstant.SCHEDULE_EXECUTION_ID);
        Long scheduleTaskId = Long.valueOf(rawTaskId);
        Long executionId = rawExecutionId == null ? null : Long.valueOf(rawExecutionId);
        try {
            agentScheduleManagementService.executeScheduled(scheduleTaskId, executionId, buildQuartzFireMetadata(context));
        } catch (Exception error) {
            throw new JobExecutionException(error, false);
        } finally {
            agentScheduleManagementService.continueFixedDelay(scheduleTaskId);
        }
    }

    /**
     * 标记本 Job 实例已中断，使尚未开始的执行立即退出。
     */
    @Override
    public void interrupt() {
        interrupted = true;
    }

    private QuartzFireMetadata buildQuartzFireMetadata(JobExecutionContext context) {
        return new QuartzFireMetadata()
                .setScheduledFireTime(context.getScheduledFireTime() == null
                        ? null : context.getScheduledFireTime().toInstant())
                .setFireTime(context.getFireTime() == null ? null : context.getFireTime().toInstant())
                .setRecovering(context.isRecovering())
                .setRefireCount(context.getRefireCount());
    }
}
