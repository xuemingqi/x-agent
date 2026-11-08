package com.x.ai.app.service.impl;

import com.x.ai.app.service.AgentAppModelService;
import com.x.ai.common.model.AgentAppModelConfig;
import com.x.ai.common.model.AiModelRuntimeConfig;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.ai.service.AiModelRuntimeService;
import com.x.framework.common.utils.UserThreadLocalUtil;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAppModelServiceImpl implements AgentAppModelService {

    private final AiModelRuntimeService modelRuntimeService;

    private final Validator validator;


    @Override
    public <T> T generate(String appId, Long modelId, String input, AgentAppModelConfig<T> configuration) {
        String userId = UserThreadLocalUtil.getUserId();
        AiModelRuntimeConfig model = modelRuntimeService.requireRuntimeModel(userId, modelId);
        RuntimeContext context = RuntimeContext.builder()
                .userId(userId)
                .sessionId("app:" + appId + ":" + UUID.randomUUID())
                .build();

        try (ReActAgent agent = ReActAgent.builder()
                .name(appId)
                .sysPrompt(configuration.getSystemPrompt())
                .model(createModel(model))
                .toolkit(new Toolkit())
                .maxIters(configuration.getMaxIterations())
                .build()) {
            Msg message = agent.call(input, configuration.getResultType(), context).block(configuration.getTimeout());
            T result = message == null ? null : message.getStructuredData(configuration.getResultType());
            if (result == null || !validator.validate(result).isEmpty()) {
                throw new XException(ResponseCodeEnum.AGENT_APP_OUTPUT_INVALID);
            }
            return result;
        } catch (XException error) {
            throw error;
        } catch (RuntimeException error) {
            log.warn("智能应用生成失败，appId={}, modelId={}, errorType={}",
                    appId, model.getModelId(), error.getClass().getSimpleName());
            throw new XException(ResponseCodeEnum.AGENT_APP_GENERATION_ERROR);
        }
    }

    /**
     * 沿用当前用户配置的模型网关与参数，由 AgentScope 处理 Class 对应的结构化输出。
     */
    private Model createModel(AiModelRuntimeConfig configuration) {
        return OpenAIChatModel.builder()
                .apiKey(configuration.getApiKey())
                .baseUrl(configuration.getBaseUrl())
                .modelName(configuration.getModelName())
                .contextWindowSize(configuration.getContextWindowSize())
                .stream(false)
                .generateOptions(GenerateOptions.builder()
                        .temperature(configuration.getTemperature())
                        .build())
                .build();
    }
}
