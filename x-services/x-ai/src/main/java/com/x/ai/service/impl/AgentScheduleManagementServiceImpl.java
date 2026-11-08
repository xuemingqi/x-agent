package com.x.ai.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.x.ai.common.constants.AgentConstant;
import com.x.ai.common.constants.PromptConstant;
import com.x.ai.common.domain.ConversationFileDo;
import com.x.ai.db.entity.AgentScheduleExecution;
import com.x.ai.db.entity.AgentScheduleTask;
import com.x.ai.service.AiModelRuntimeService;
import com.x.ai.db.enums.AgentScheduleExecutionStatus;
import com.x.ai.db.enums.AgentScheduleMode;
import com.x.ai.db.enums.AgentScheduleStatus;
import com.x.ai.db.enums.AgentScheduleTriggerSource;
import com.x.ai.db.service.AgentScheduleExecutionIService;
import com.x.ai.db.service.AgentScheduleTaskIService;
import com.x.ai.common.domain.AgentResponseDo;
import com.x.ai.common.domain.AgentScheduleExecutionDo;
import com.x.ai.common.domain.AgentScheduleOperationDo;
import com.x.ai.common.domain.AgentScheduleTaskDo;
import com.x.ai.common.dto.AgentRequestDto;
import com.x.ai.common.model.AgentScheduleCreateParam;
import com.x.ai.common.model.AgentScheduleQueryParam;
import com.x.ai.common.model.AgentScheduleUpdateParam;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.response.BasePageResult;
import com.x.framework.common.utils.DateUtil;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.common.model.QuartzFireMetadata;
import com.x.ai.common.model.UserContext;
import com.x.ai.scheduler.service.QuartzAgentSchedulerService;
import com.x.ai.service.AgentExecutionService;
import com.x.ai.service.AgentScheduleManagementService;
import com.x.ai.service.ConversationFileService;
import io.agentscope.extensions.scheduler.config.ScheduleConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentScheduleManagementServiceImpl implements AgentScheduleManagementService {

    private static final int DEFAULT_PAGE = 1;

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 200;

    private static final int RESULT_SUMMARY_MAX_LENGTH = 2000;

    private static final int ERROR_SUMMARY_MAX_LENGTH = 100;

    private final AgentScheduleTaskIService agentScheduleTaskIService;

    private final AgentScheduleExecutionIService agentScheduleExecutionIService;

    private final QuartzAgentSchedulerService quartzAgentSchedulerService;

    private final AgentExecutionService agentExecutionService;

    private final ConversationFileService conversationFileService;

    private final AiModelRuntimeService aiModelRuntimeService;

    private final Snowflake snowflake;


    @Override
    public AgentScheduleTaskDo create(String userId, AgentScheduleCreateParam param) {
        validateTask(param.getTaskName(), param.getPrompt(), param.getModelId());
        aiModelRuntimeService.requireRuntimeModel(userId, param.getModelId());
        ScheduleConfig config = scheduleConfig(param.getScheduleMode(), param.getCronExpression(),
                param.getFixedRateMs(), param.getFixedDelayMs(), param.getInitialDelayMs());
        AgentScheduleTask task = AgentScheduleTask.builder()
                .id(snowflake.nextId())
                .taskName(param.getTaskName().trim())
                .userId(userId)
                .modelId(param.getModelId())
                .prompt(param.getPrompt().trim())
                .scheduleMode(param.getScheduleMode())
                .cronExpression(param.getCronExpression())
                .fixedRateMs(param.getFixedRateMs())
                .fixedDelayMs(param.getFixedDelayMs())
                .initialDelayMs(config.getInitialDelay())
                .status(AgentScheduleStatus.ACTIVE)
                .build();
        quartzAgentSchedulerService.schedule(task.getId(), config);
        agentScheduleTaskIService.save(task);
        return toTaskDo(task);
    }

    @Override
    public BasePageResult<List<AgentScheduleTaskDo>> page(String userId, AgentScheduleQueryParam param) {
        if (StringUtil.isEmpty(userId)) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }

        int page = param.getPage() == null ? DEFAULT_PAGE : param.getPage();
        int pageSize = param.getPageSize() == null ? DEFAULT_PAGE_SIZE : param.getPageSize();
        validatePage(page, pageSize);

        IPage<AgentScheduleTask> result = agentScheduleTaskIService.lambdaQuery()
                .eq(AgentScheduleTask::getUserId, userId)
                .eq(param.getScheduleMode() != null, AgentScheduleTask::getScheduleMode, param.getScheduleMode())
                .eq(param.getStatus() != null, AgentScheduleTask::getStatus, param.getStatus())
                .like(param.getTaskName() != null && !param.getTaskName().isBlank(),
                        AgentScheduleTask::getTaskName, param.getTaskName())
                .orderByDesc(AgentScheduleTask::getUpdateTime)
                .page(new Page<>(page, pageSize));

        List<AgentScheduleTaskDo> rows = result.getRecords().stream()
                .map(this::toTaskDo).toList();
        return BasePageResult.getInstance(page, pageSize, Math.toIntExact(result.getTotal()), rows);
    }

    @Override
    public List<AgentScheduleTaskDo> list(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        return agentScheduleTaskIService.lambdaQuery()
                .eq(AgentScheduleTask::getUserId, userId)
                .orderByDesc(AgentScheduleTask::getUpdateTime)
                .list()
                .stream()
                .map(this::toTaskDo)
                .toList();
    }

    @Override
    public AgentScheduleTaskDo get(String userId, Long id) {
        return toTaskDo(ownedTask(userId, id));
    }

    @Override
    public AgentScheduleTaskDo update(String userId, Long id, AgentScheduleUpdateParam param) {
        AgentScheduleTask task = ownedTask(userId, id);
        validateTask(param.getTaskName(), param.getPrompt(), param.getModelId());
        aiModelRuntimeService.requireRuntimeModel(userId, param.getModelId());
        ScheduleConfig config = scheduleConfig(param.getScheduleMode(), param.getCronExpression(),
                param.getFixedRateMs(), param.getFixedDelayMs(), param.getInitialDelayMs());

        if (!quartzAgentSchedulerService.reschedule(task.getId(), config)) {
            throw new XException(ResponseCodeEnum.QUARTZ_SCHEDULER_ERROR);
        }
        if (task.getStatus() == AgentScheduleStatus.PAUSED) {
            quartzAgentSchedulerService.pause(task.getId());
        }

        task.setTaskName(param.getTaskName().trim())
                .setModelId(param.getModelId())
                .setPrompt(param.getPrompt().trim())
                .setScheduleMode(param.getScheduleMode())
                .setCronExpression(param.getCronExpression())
                .setFixedRateMs(param.getFixedRateMs())
                .setFixedDelayMs(param.getFixedDelayMs())
                .setInitialDelayMs(config.getInitialDelay());
        agentScheduleTaskIService.updateById(task);
        return toTaskDo(task);
    }

    @Override
    public AgentScheduleOperationDo pause(String userId, Long id) {
        AgentScheduleTask task = ownedTask(userId, id);
        if (task.getStatus() == AgentScheduleStatus.PAUSED) {
            return operation(task, "already paused");
        }
        quartzAgentSchedulerService.pause(task.getId());
        task.setStatus(AgentScheduleStatus.PAUSED);
        agentScheduleTaskIService.updateById(task);
        return operation(task, "paused");
    }

    @Override
    public AgentScheduleOperationDo resume(String userId, Long id) {
        AgentScheduleTask task = ownedTask(userId, id);
        if (task.getStatus() == AgentScheduleStatus.ACTIVE) {
            return operation(task, "already active");
        }
        quartzAgentSchedulerService.resume(task.getId());
        task.setStatus(AgentScheduleStatus.ACTIVE);
        agentScheduleTaskIService.updateById(task);
        return operation(task, "resumed");
    }

    @Override
    public AgentScheduleOperationDo delete(String userId, Long id) {
        AgentScheduleTask task = ownedTask(userId, id);
        if (!quartzAgentSchedulerService.cancel(task.getId())) {
            throw new XException(ResponseCodeEnum.QUARTZ_SCHEDULER_ERROR);
        }
        agentScheduleTaskIService.removeById(task.getId());
        return operation(task, "deleted");
    }

    @Override
    public AgentScheduleExecutionDo runNow(String userId, Long id) {
        AgentScheduleTask task = ownedTask(userId, id);
        AgentScheduleExecution execution = newExecution(task, AgentScheduleTriggerSource.MANUAL, null, null);
        if (!quartzAgentSchedulerService.triggerNow(task.getId(), execution.getId())) {
            throw new XException(ResponseCodeEnum.QUARTZ_SCHEDULER_ERROR);
        }
        return toExecutionDo(execution, List.of());
    }

    @Override
    public BasePageResult<List<AgentScheduleExecutionDo>> executions(String userId, Long id, int page, int pageSize) {
        ownedTask(userId, id);
        validatePage(page, pageSize);
        IPage<AgentScheduleExecution> result = agentScheduleExecutionIService.lambdaQuery()
                .eq(AgentScheduleExecution::getScheduleTaskId, id)
                .eq(AgentScheduleExecution::getUserId, userId)
                .orderByDesc(AgentScheduleExecution::getCreateTime)
                .page(new Page<>(page, pageSize));
        Map<String, List<ConversationFileDo>> filesBySession = conversationFileService.listPublishedOutputs(userId,
                result.getRecords().stream().map(this::scheduleExecutionSessionId).toList());
        List<AgentScheduleExecutionDo> rows = result.getRecords().stream()
                .map(execution -> toExecutionDo(execution,
                        filesBySession.getOrDefault(scheduleExecutionSessionId(execution), List.of())))
                .toList();
        return BasePageResult.getInstance(page, pageSize, Math.toIntExact(result.getTotal()), rows);
    }

    @Override
    public void executeScheduled(Long scheduleTaskId, Long executionId, QuartzFireMetadata metadata) {
        if (scheduleTaskId == null || metadata == null) {
            throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
        }
        AgentScheduleTask task = agentScheduleTaskIService.getById(scheduleTaskId);
        if (task == null || task.getStatus() != AgentScheduleStatus.ACTIVE) {
            return;
        }
        Instant scheduled = metadata.getScheduledFireTime() == null
                ? metadata.getFireTime() : metadata.getScheduledFireTime();
        AgentScheduleTriggerSource source = executionId == null
                ? AgentScheduleTriggerSource.SCHEDULED : AgentScheduleTriggerSource.MANUAL;
        AgentScheduleExecution execution = newExecution(task, source, scheduled, executionId);
        UserContext owner = new UserContext()
                .setUserId(task.getUserId());
        execute(task, execution, owner);
    }

    @Override
    public void continueFixedDelay(Long scheduleTaskId) {
        if (scheduleTaskId == null) {
            return;
        }
        AgentScheduleTask task = agentScheduleTaskIService.getById(scheduleTaskId);
        if (task == null || task.getStatus() != AgentScheduleStatus.ACTIVE
                || task.getScheduleMode() != AgentScheduleMode.FIXED_DELAY) {
            return;
        }
        ScheduleConfig next = scheduleConfig(task.getScheduleMode(), task.getCronExpression(),
                task.getFixedRateMs(), task.getFixedDelayMs(), task.getInitialDelayMs());
        if (!quartzAgentSchedulerService.reschedule(scheduleTaskId, next, task.getFixedDelayMs())) {
            throw new XException(ResponseCodeEnum.QUARTZ_SCHEDULER_ERROR);
        }
    }

    /**
     * 执行一次定时任务并持久化 CREATED、RUNNING 及最终状态，同时回写任务最近执行信息。
     */
    private void execute(AgentScheduleTask task, AgentScheduleExecution execution, UserContext userContext) {
        agentScheduleExecutionIService.save(execution);
        execution.setStatus(AgentScheduleExecutionStatus.RUNNING)
                .setAttemptCount(1);
        agentScheduleExecutionIService.updateById(execution);
        try {
            String executionSessionId = scheduleExecutionSessionId(execution);
            AgentResponseDo response = agentExecutionService.runStandalone(userContext,
                    new AgentRequestDto()
                            .setRequest(PromptConstant.SCHEDULE_EXECUTION_PROMPT.formatted(task.getPrompt(),
                                    AgentConstant.CONVERSATION_FILE_OUTPUT_PATH_TEMPLATE.formatted(executionSessionId)))
                            .setSessionId(executionSessionId)
                            .setModelId(task.getModelId()));
            execution.setTaskId(response.getTaskId())
                    .setModelId(response.getModelId())
                    .setStatus(AgentScheduleExecutionStatus.SUCCESS)
                    .setResultSummary(StringUtil.subPre(response.getText(), RESULT_SUMMARY_MAX_LENGTH));
        } catch (RuntimeException error) {
            execution.setStatus(AgentScheduleExecutionStatus.FAILED)
                    .setErrorSummary(sanitize(error));
        } finally {
            LocalDateTime finish = LocalDateTime.now();
            execution.setFinishTime(finish);
            agentScheduleExecutionIService.updateById(execution);
            task.setLastRunTime(finish)
                    .setLastExecutionStatus(execution.getStatus());
            agentScheduleTaskIService.updateById(task);
        }
    }

    /**
     * 根据任务快照与触发元数据创建尚未持久化的执行记录。
     */
    private AgentScheduleExecution newExecution(AgentScheduleTask task, AgentScheduleTriggerSource source,
                                                Instant scheduledFireTime, Long executionId) {
        return AgentScheduleExecution.builder()
                .id(executionId == null ? snowflake.nextId() : executionId)
                .scheduleTaskId(task.getId())
                .triggerSource(source)
                .scheduledFireTime(DateUtil.toLocalDateTime(scheduledFireTime))
                .userId(task.getUserId())
                .modelId(task.getModelId())
                .status(AgentScheduleExecutionStatus.CREATED)
                .attemptCount(0)
                .build();
    }

    /**
     * 查询当前用户拥有的定时任务；不存在时抛出任务不存在异常。
     *
     * @param userId 用户 ID
     * @param id     定时任务主键
     * @return 当前用户拥有的任务实体
     */
    private AgentScheduleTask ownedTask(String userId, Long id) {
        AgentScheduleTask task = agentScheduleTaskIService.lambdaQuery()
                .eq(AgentScheduleTask::getId, id)
                .eq(AgentScheduleTask::getUserId, userId)
                .one();
        if (task == null) {
            throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_NOT_FOUND);
        }
        return task;
    }

    private static AgentScheduleOperationDo operation(AgentScheduleTask task, String message) {
        return new AgentScheduleOperationDo()
                .setId(task.getId())
                .setStatus(task.getStatus())
                .setMessage(message);
    }

    private AgentScheduleTaskDo toTaskDo(AgentScheduleTask task) {
        return new AgentScheduleTaskDo()
                .setId(task.getId()).setTaskName(task.getTaskName())
                .setUserId(task.getUserId())
                .setModelId(task.getModelId()).setPrompt(task.getPrompt())
                .setScheduleMode(task.getScheduleMode())
                .setCronExpression(task.getCronExpression())
                .setFixedRateMs(task.getFixedRateMs()).setFixedDelayMs(task.getFixedDelayMs())
                .setInitialDelayMs(task.getInitialDelayMs())
                .setStatus(task.getStatus())
                .setLastRunTime(task.getLastRunTime())
                .setNextRunTime(quartzAgentSchedulerService.state(task.getId())
                        .map(state -> DateUtil.toLocalDateTime(state.getNextFireTime())).orElse(null))
                .setLastExecutionStatus(task.getLastExecutionStatus())
                .setCreateTime(task.getCreateTime())
                .setUpdateTime(task.getUpdateTime());
    }

    /**
     * 将执行记录及其发布文件转换为接口模型。
     */
    private AgentScheduleExecutionDo toExecutionDo(AgentScheduleExecution execution, List<ConversationFileDo> files) {
        return new AgentScheduleExecutionDo()
                .setId(execution.getId())
                .setScheduleTaskId(execution.getScheduleTaskId())
                .setTriggerSource(execution.getTriggerSource())
                .setScheduledFireTime(execution.getScheduledFireTime())
                .setTaskId(execution.getTaskId())
                .setOwnerUserId(execution.getUserId())
                .setModelId(execution.getModelId()).setStatus(execution.getStatus())
                .setAttemptCount(execution.getAttemptCount())
                .setStartTime(execution.getStartTime()).setFinishTime(execution.getFinishTime())
                .setResultSummary(execution.getResultSummary())
                .setErrorSummary(execution.getErrorSummary())
                .setFiles(List.copyOf(files))
                .setCreateTime(execution.getCreateTime())
                .setUpdateTime(execution.getUpdateTime());
    }

    /**
     * 每次定时任务执行使用独立且可由执行记录主键稳定推导的会话 ID。
     */
    private String scheduleExecutionSessionId(AgentScheduleExecution execution) {
        return AgentConstant.SCHEDULE_SESSION_PREFIX + execution.getId();
    }

    private static ScheduleConfig scheduleConfig(AgentScheduleMode mode, String cronExpression,
                                                 Long fixedRateMs, Long fixedDelayMs, Long initialDelayMs) {
        if (mode == null || initialDelayMs != null && initialDelayMs < 0) {
            throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
        }
        ScheduleConfig.Builder builder = ScheduleConfig.builder()
                .initialDelay(initialDelayMs == null ? 0L : initialDelayMs);
        switch (mode) {
            case CRON -> {
                if (cronExpression == null || !CronExpression.isValidExpression(cronExpression)) {
                    throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
                }
                builder.cron(cronExpression);
            }
            case FIXED_RATE -> {
                if (fixedRateMs == null || fixedRateMs <= 0
                        || fixedRateMs % AgentConstant.SCHEDULE_MILLIS_PER_SECOND != 0) {
                    throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
                }
                builder.fixedRate(fixedRateMs);
            }
            case FIXED_DELAY -> {
                if (fixedDelayMs == null || fixedDelayMs <= 0
                        || fixedDelayMs % AgentConstant.SCHEDULE_MILLIS_PER_SECOND != 0) {
                    throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
                }
                builder.fixedDelay(fixedDelayMs);
            }
        }
        return builder.build();
    }

    /**
     * 校验定时任务必需的业务输入。
     */
    private static void validateTask(String taskName, String prompt, Long modelId) {
        if (StringUtil.isBlank(taskName) || StringUtil.isBlank(prompt) || modelId == null || modelId <= 0) {
            throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
        }
    }

    private static void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new XException(ResponseCodeEnum.AGENT_SCHEDULE_INVALID);
        }
    }

    /**
     * 将异常转换为可持久化且长度受控的错误摘要。
     */
    private String sanitize(Throwable error) {
        String message = StringUtil.isEmpty(error.getMessage()) ? error.getClass().getSimpleName() : error.getMessage();
        return StringUtil.subPre(message, ERROR_SUMMARY_MAX_LENGTH);
    }
}
