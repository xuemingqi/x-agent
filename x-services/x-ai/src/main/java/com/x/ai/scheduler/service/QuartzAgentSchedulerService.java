package com.x.ai.scheduler.service;

import com.x.ai.common.constants.AgentConstant;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.ai.common.functional.QuartzAction;
import com.x.ai.common.model.QuartzTaskState;
import com.x.ai.properties.AgentProperties;
import com.x.ai.scheduler.job.QuartzJob;
import com.x.ai.scheduler.task.AgentScheduleTask;
import io.agentscope.core.message.Msg;
import io.agentscope.extensions.scheduler.AgentScheduler;
import io.agentscope.extensions.scheduler.ScheduleAgentTask;
import io.agentscope.extensions.scheduler.config.AgentConfig;
import io.agentscope.extensions.scheduler.config.ScheduleConfig;
import lombok.RequiredArgsConstructor;
import org.quartz.CalendarIntervalScheduleBuilder;
import org.quartz.CalendarIntervalTrigger;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.DailyTimeIntervalScheduleBuilder;
import org.quartz.DailyTimeIntervalTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;

@Service
@RequiredArgsConstructor
public class QuartzAgentSchedulerService implements AgentScheduler {

    private final Scheduler scheduler;

    private final AgentProperties agentProperties;


    public ScheduleAgentTask<Msg> schedule(long scheduleTaskId, ScheduleConfig config) {
        if (scheduleTaskId <= 0 || config == null) {
            throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
        }
        JobKey jobKey = jobKey(scheduleTaskId);
        JobDetail jobDetail = JobBuilder.newJob(QuartzJob.class)
                .withIdentity(jobKey)
                .usingJobData(AgentConstant.SCHEDULE_TASK_ID, String.valueOf(scheduleTaskId))
                .build();
        try {
            scheduler.scheduleJob(jobDetail, createTrigger(jobKey, config, config.getInitialDelay()));
            return new AgentScheduleTask(scheduleTaskId, this);
        } catch (SchedulerException error) {
            throw new XException(ResponseCodeEnum.QUARTZ_SCHEDULER_ERROR);
        }
    }

    @Override
    public ScheduleAgentTask<Msg> schedule(AgentConfig agentConfig, ScheduleConfig scheduleConfig) {
        if (agentConfig == null) {
            throw new XException(ResponseCodeEnum.AGENT_CONFIG_INVALID);
        }
        return schedule(parseTaskId(agentConfig.getName()), scheduleConfig);
    }

    public boolean pause(long scheduleTaskId) {
        return execute(() -> scheduler.pauseJob(jobKey(scheduleTaskId)));
    }

    public boolean resume(long scheduleTaskId) {
        return execute(() -> scheduler.resumeJob(jobKey(scheduleTaskId)));
    }

    public boolean cancel(long scheduleTaskId) {
        if (scheduleTaskId <= 0) {
            throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
        }
        try {
            return scheduler.deleteJob(jobKey(scheduleTaskId));
        } catch (SchedulerException error) {
            throw new XException(ResponseCodeEnum.QUARTZ_SCHEDULER_ERROR);
        }
    }

    public boolean reschedule(long scheduleTaskId, ScheduleConfig config) {
        return reschedule(scheduleTaskId, config, config == null ? null : config.getInitialDelay());
    }

    public boolean reschedule(long scheduleTaskId, ScheduleConfig config, Long triggerStartDelayMs) {
        if (scheduleTaskId <= 0 || config == null || triggerStartDelayMs == null || triggerStartDelayMs < 0) {
            throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
        }
        try {
            Trigger trigger = createTrigger(jobKey(scheduleTaskId), config, triggerStartDelayMs);
            return scheduler.rescheduleJob(triggerKey(scheduleTaskId), trigger) != null;
        } catch (SchedulerException error) {
            throw new XException(ResponseCodeEnum.QUARTZ_SCHEDULER_ERROR);
        }
    }

    public boolean triggerNow(long scheduleTaskId) {
        return triggerNow(scheduleTaskId, null);
    }

    public boolean triggerNow(long scheduleTaskId, Long executionId) {
        JobDataMap data = new JobDataMap();
        if (executionId != null) {
            data.put(AgentConstant.SCHEDULE_EXECUTION_ID, String.valueOf(executionId));
        }
        return execute(() -> scheduler.triggerJob(jobKey(scheduleTaskId), data));
    }

    public Optional<QuartzTaskState> state(long scheduleTaskId) {
        if (scheduleTaskId <= 0) {
            throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
        }
        TriggerKey triggerKey = triggerKey(scheduleTaskId);
        try {
            if (!scheduler.checkExists(jobKey(scheduleTaskId))) {
                return Optional.empty();
            }
            Trigger trigger = scheduler.getTrigger(triggerKey);
            return Optional.of(new QuartzTaskState()
                    .setExists(true)
                    .setTriggerState(trigger == null ? Trigger.TriggerState.NONE
                            : scheduler.getTriggerState(triggerKey))
                    .setNextFireTime(trigger == null || trigger.getNextFireTime() == null
                            ? null : trigger.getNextFireTime().toInstant()));
        } catch (SchedulerException error) {
            throw new XException(ResponseCodeEnum.QUARTZ_SCHEDULER_ERROR);
        }
    }

    private Trigger createTrigger(JobKey jobKey, ScheduleConfig config, Long startDelayMs) {
        long delay = startDelayMs == null ? 0L : startDelayMs;
        if (delay < 0) {
            throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
        }
        Instant startTime = Instant.now().plusMillis(delay);
        return switch (config.getScheduleMode()) {
            case CRON -> createCronTrigger(jobKey, config, startTime);
            case FIXED_RATE -> createFixedRateTrigger(jobKey, config, startTime);
            case FIXED_DELAY -> createFixedDelayTrigger(jobKey, config, startTime);
            case NONE -> throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
        };
    }

    private CronTrigger createCronTrigger(JobKey jobKey, ScheduleConfig config, Instant startTime) {
        CronScheduleBuilder schedule = CronScheduleBuilder.cronSchedule(config.getCronExpression())
                .withMisfireHandlingInstructionDoNothing();
        if (config.getZoneId() != null && !config.getZoneId().isBlank()) {
            schedule = schedule.inTimeZone(TimeZone.getTimeZone(config.getZoneId()));
        }
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey(jobKey))
                .forJob(jobKey)
                .startAt(Date.from(startTime))
                .withSchedule(schedule)
                .build();
    }

    private CalendarIntervalTrigger createFixedRateTrigger(JobKey jobKey, ScheduleConfig config,
                                                            Instant startTime) {
        CalendarIntervalScheduleBuilder schedule = CalendarIntervalScheduleBuilder.calendarIntervalSchedule()
                .withIntervalInSeconds(intervalSeconds(config.getFixedRate()))
                .withMisfireHandlingInstructionDoNothing();
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey(jobKey))
                .forJob(jobKey)
                .startAt(Date.from(startTime))
                .withSchedule(schedule)
                .build();
    }

    private DailyTimeIntervalTrigger createFixedDelayTrigger(JobKey jobKey, ScheduleConfig config,
                                                              Instant startTime) {
        DailyTimeIntervalScheduleBuilder schedule = DailyTimeIntervalScheduleBuilder.dailyTimeIntervalSchedule()
                .withIntervalInSeconds(intervalSeconds(config.getFixedDelay()))
                .withRepeatCount(0)
                .withMisfireHandlingInstructionDoNothing();
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey(jobKey))
                .forJob(jobKey)
                .startAt(Date.from(startTime))
                .withSchedule(schedule)
                .build();
    }

    private int intervalSeconds(Long intervalMs) {
        if (intervalMs == null || intervalMs <= 0 || intervalMs % AgentConstant.SCHEDULE_MILLIS_PER_SECOND != 0
                || intervalMs / AgentConstant.SCHEDULE_MILLIS_PER_SECOND > Integer.MAX_VALUE) {
            throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
        }
        return Math.toIntExact(intervalMs / AgentConstant.SCHEDULE_MILLIS_PER_SECOND);
    }

    @Override
    public boolean cancel(String name) {
        try {
            return cancel(parseTaskId(name));
        } catch (XException error) {
            return false;
        }
    }

    @Override
    public ScheduleAgentTask<Msg> getScheduledAgent(String name) {
        try {
            long taskId = parseTaskId(name);
            return state(taskId).isPresent() ? new AgentScheduleTask(taskId, this) : null;
        } catch (XException error) {
            return null;
        }
    }

    @Override
    public List<ScheduleAgentTask> getAllScheduleAgentTasks() {
        try {
            Set<JobKey> keys = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(Scheduler.DEFAULT_GROUP));
            List<ScheduleAgentTask> tasks = new ArrayList<>();
            for (JobKey key : keys) {
                if (key.getName().startsWith(AgentConstant.SCHEDULE_JOB_PREFIX)) {
                    tasks.add(new AgentScheduleTask(taskId(key), this));
                }
            }
            return tasks;
        } catch (SchedulerException error) {
            throw new XException(ResponseCodeEnum.QUARTZ_SCHEDULER_ERROR);
        }
    }

    @Override
    public void shutdown() {
        try {
            scheduler.standby();
        } catch (SchedulerException error) {
            throw new XException(ResponseCodeEnum.QUARTZ_SCHEDULER_ERROR);
        }
    }

    @Override
    public String getSchedulerType() {
        return agentProperties.getSchedule().getQuartz().getSchedulerType();
    }

    private boolean execute(QuartzAction action) {
        try {
            action.run();
            return true;
        } catch (SchedulerException error) {
            throw new XException(ResponseCodeEnum.QUARTZ_SCHEDULER_ERROR);
        }
    }

    private static long parseTaskId(String value) {
        try {
            long taskId = Long.parseLong(value);
            if (taskId <= 0) {
                throw new NumberFormatException();
            }
            return taskId;
        } catch (NumberFormatException | NullPointerException error) {
            throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
        }
    }

    private static JobKey jobKey(long taskId) {
        return new JobKey(AgentConstant.SCHEDULE_JOB_PREFIX + taskId);
    }

    private static TriggerKey triggerKey(long taskId) {
        return new TriggerKey(AgentConstant.SCHEDULE_TRIGGER_PREFIX + taskId);
    }

    private static TriggerKey triggerKey(JobKey jobKey) {
        return triggerKey(taskId(jobKey));
    }

    private static long taskId(JobKey jobKey) {
        if (jobKey == null || !jobKey.getName().startsWith(AgentConstant.SCHEDULE_JOB_PREFIX)) {
            throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
        }
        return parseTaskId(jobKey.getName().substring(AgentConstant.SCHEDULE_JOB_PREFIX.length()));
    }

}
