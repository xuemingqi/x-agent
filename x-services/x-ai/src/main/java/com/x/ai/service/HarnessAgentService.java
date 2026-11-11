package com.x.ai.service;

import com.x.ai.common.model.AiModelRuntimeConfig;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface HarnessAgentService {

    /**
     * 获取指定用户和模型对应的独享 HarnessAgent 实例。
     *
     * @param model 已完成用户归属校验的运行配置
     * @param userId 用户 ID
     * @return HarnessAgent 实例
     */
    HarnessAgent getAgent(AiModelRuntimeConfig model, String userId);

    /**
     * 使指定用户缓存的 Agent 失效，让后续调用加载最新 MCP 配置。
     * 已经开始的调用不会切换 Toolkit。
     *
     * @param userId 用户 ID
     */
    void evictUserAgents(String userId);

    /**
     * 阻塞执行 HarnessAgent，并自动处理工具审批。
     *
     * @param agent HarnessAgent 实例
     * @param input 本轮输入消息
     * @param context 运行时上下文
     * @return 最终生成结果
     */
    Mono<Msg> executeBlocking(HarnessAgent agent, Msg input, RuntimeContext context);

    /**
     * 流式执行 HarnessAgent，并自动处理工具审批。
     *
     * @param agent HarnessAgent 实例
     * @param input 本轮输入消息
     * @param context 运行时上下文
     * @return Agent 事件流
     */
    Flux<AgentEvent> executeStream(HarnessAgent agent, Msg input, RuntimeContext context);

}
