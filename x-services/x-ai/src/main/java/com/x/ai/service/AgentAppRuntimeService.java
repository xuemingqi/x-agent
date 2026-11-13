package com.x.ai.service;

import com.x.ai.common.domain.AgentAppGenerateDo;
import com.x.ai.common.domain.AgentAppRunDo;
import com.x.ai.common.domain.HttpExecutionDo;
import com.x.ai.common.dto.AgentAppDataSaveDto;
import com.x.ai.common.dto.AgentAppGenerateDto;
import com.x.ai.common.dto.AgentAppRunDto;
import com.x.ai.common.dto.AgentAppHttpRequestDto;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

public interface AgentAppRuntimeService {

    /**
     * 在核对用户、版本与 http.request 权限后转发一次 HTTP 请求，保留响应式取消链。
     */
    Mono<HttpExecutionDo> requestHttp(String appId, AgentAppHttpRequestDto request);

    /**
     * 使用当前用户的模型生成文本，仅允许当前发布版本已声明 model.generate 的应用调用。
     */
    AgentAppGenerateDo generate(String appId, AgentAppGenerateDto request);

    /**
     * 执行一次独立 Agent 任务，仅开放当前用户已启用的 MCP 工具，不继承平台管理工具或聊天历史。
     * 应用的当前发布版本必须声明 agent.run 权限。
     */
    AgentAppRunDo run(String appId, AgentAppRunDto request);

    /**
     * 读取当前用户在应用内保存的 JSON 值；键不存在时返回 null，需要 storage 权限。
     */
    JsonNode getData(String appId, String version, String key);

    /**
     * 原子写入当前用户的应用数据，单值最多 1 MiB，需要 storage 权限。
     */
    boolean saveData(String appId, String version, String key, AgentAppDataSaveDto request);

    /**
     * 幂等删除当前用户的应用数据，需要 storage 权限。
     */
    boolean deleteData(String appId, String version, String key);
}
