package com.x.ai.tool;

import com.x.ai.common.dto.AgentScheduleToolExecutionDto;
import com.x.ai.common.dto.AgentScheduleToolOperationDto;
import com.x.ai.common.dto.AgentScheduleToolCreateDto;
import com.x.ai.common.dto.AgentScheduleToolDetailDto;
import com.x.ai.common.dto.AgentScheduleToolSummaryDto;
import com.x.ai.common.dto.AgentScheduleToolUpdateDto;
import com.x.ai.common.model.ToolContext;
import com.x.framework.common.response.BasePageResult;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.service.AgentScheduleToolService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AgentScheduleTools {

    private final AgentScheduleToolService agentScheduleToolService;


    @Tool(name = "schedule_task_create", description = """
            创建 Agent 定时任务。
            定时任务独立执行，prompt 必须包含完整工作要求，不能依赖当前聊天上下文。
            返回必要配置：id 为任务 ID，taskName 为任务名称，prompt 为执行提示词；
            scheduleMode 为调度模式（1-Cron 表达式、2-固定频率、3-固定延迟）；
            cronExpression、fixedRateMs、fixedDelayMs 分别对应三种调度模式，initialDelayMs 为首次执行延迟毫秒数；
            status 为任务状态（1-启用、2-已暂停），nextRunTime 为下次执行时间。
            """)
    public AgentScheduleToolDetailDto create(@ToolParam(name = "request", description = "定时任务完整配置") AgentScheduleToolCreateDto request,
                                             ToolContext toolContext) {
        return agentScheduleToolService.create(toolContext.getUserId(), toolContext.getModelId(), request);
    }

    @Tool(name = "schedule_task_list", description = """
            查询当前用户的 Agent 定时任务，只返回用于识别和选择任务的摘要列表。
            返回字段：id 为任务 ID，taskName 为任务名称；
            scheduleMode 为调度模式（1-Cron 表达式、2-固定频率、3-固定延迟）；
            status 为任务状态（1-启用、2-已暂停），nextRunTime 为下次执行时间。
            """, readOnly = true)
    public List<AgentScheduleToolSummaryDto> list(ToolContext toolContext) {
        return agentScheduleToolService.list(toolContext.getUserId());
    }

    @Tool(name = "schedule_task_get", description = """
            查询当前用户的单个定时任务，返回修改任务所需的必要配置。
            返回字段：id 为任务 ID，taskName 为任务名称，prompt 为执行提示词；
            scheduleMode 为调度模式（1-Cron 表达式、2-固定频率、3-固定延迟）；
            cronExpression、fixedRateMs、fixedDelayMs 分别对应三种调度模式，initialDelayMs 为首次执行延迟毫秒数；
            status 为任务状态（1-启用、2-已暂停），nextRunTime 为下次执行时间。
            """, readOnly = true)
    public AgentScheduleToolDetailDto get(@ToolParam(name = "id", description = "定时任务 ID") String id,
                                          ToolContext toolContext) {
        return agentScheduleToolService.get(toolContext.getUserId(), StringUtil.parseLongId(id));
    }

    @Tool(name = "schedule_task_update", description = """
            更新当前用户的定时任务。
            prompt 是完整替换配置，必须保留仍有效的工作要求。
            返回更新后的必要配置：id 为任务 ID，taskName 为任务名称，prompt 为执行提示词；
            scheduleMode 为调度模式（1-Cron 表达式、2-固定频率、3-固定延迟）；
            cronExpression、fixedRateMs、fixedDelayMs 分别对应三种调度模式，initialDelayMs 为首次执行延迟毫秒数；
            status 为任务状态（1-启用、2-已暂停），nextRunTime 为下次执行时间。
            """)
    public AgentScheduleToolDetailDto update(@ToolParam(name = "id", description = "定时任务 ID") String id,
                                             @ToolParam(name = "request", description = "新的完整配置")
                                             AgentScheduleToolUpdateDto request, ToolContext toolContext) {
        return agentScheduleToolService.update(toolContext.getUserId(), toolContext.getModelId(),
                StringUtil.parseLongId(id), request);
    }

    @Tool(name = "schedule_task_pause", description = "暂停当前用户的定时任务")
    public AgentScheduleToolOperationDto pause(@ToolParam(name = "id", description = "定时任务 ID") String id,
                                          ToolContext toolContext) {
        return agentScheduleToolService.pause(toolContext.getUserId(), StringUtil.parseLongId(id));
    }

    @Tool(name = "schedule_task_resume", description = "恢复当前用户的定时任务")
    public AgentScheduleToolOperationDto resume(@ToolParam(name = "id", description = "定时任务 ID") String id,
                                           ToolContext toolContext) {
        return agentScheduleToolService.resume(toolContext.getUserId(), StringUtil.parseLongId(id));
    }

    @Tool(name = "schedule_task_delete", description = "删除当前用户的定时任务")
    public AgentScheduleToolOperationDto delete(@ToolParam(name = "id", description = "定时任务 ID") String id,
                                           ToolContext toolContext) {
        return agentScheduleToolService.delete(toolContext.getUserId(), StringUtil.parseLongId(id));
    }

    @Tool(name = "schedule_task_run_now",
            description = "异步触发当前用户的定时任务并立即返回执行记录；本轮不要等待或查询执行结果")
    public AgentScheduleToolExecutionDto runNow(@ToolParam(name = "id", description = "定时任务 ID") String id,
                                           ToolContext toolContext) {
        return agentScheduleToolService.runNow(toolContext.getUserId(), StringUtil.parseLongId(id));
    }

    @Tool(name = "schedule_task_executions", readOnly = true, description = """
            分页查询当前用户指定定时任务的执行记录，按创建时间倒序，默认第 1 页、每页 20 条。
            id 必须使用 schedule_task_list/get 返回的任务 ID 字符串，不能传执行记录 ID 或 JSON 数字。
            返回 pageData 分页信息和 results 记录，hasNextPages=true 时可继续查询下一页。
            每条记录包含字符串 id、scheduleTaskId、modelId，状态 status：1-已创建、2-执行中、3-成功、4-失败、5-中断；
            triggerSource：1-定时、2-手动；时间为平台本地 ISO 时间。
            resultSummary/errorSummary 为结果或错误摘要，files 为已发布文件，不代表完整对话正文。
            用于用户询问执行历史或结果；刚调用 schedule_task_run_now 时本轮不要循环轮询，记录未结束时如实说明。
            """)
    public BasePageResult<List<AgentScheduleToolExecutionDto>> executions(@ToolParam(name = "id", description = "定时任务 ID，必须传字符串") String id,
            @ToolParam(name = "page", description = "页码，从 1 开始；省略默认 1", required = false) Integer page,
            @ToolParam(name = "pageSize", description = "每页 1-200 条；省略默认 20", required = false) Integer pageSize, ToolContext toolContext) {
        return agentScheduleToolService.executions(toolContext.getUserId(), StringUtil.parseLongId(id), page, pageSize);
    }
}
