package com.x.ai.service.impl;

import com.x.ai.common.model.AgentRuntimeConfiguration;
import com.x.ai.common.model.AiModelRuntimeConfig;
import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.common.model.McpRuntimeSnapshot;
import com.x.ai.common.model.SkillRuntimeSnapshot;
import com.x.ai.db.enums.AgentModelMultimodalCapability;
import com.x.ai.service.McpRuntimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class HarnessAgentConfigurationTest {

    @Mock
    private McpRuntimeService mcpRuntimeService;

    @InjectMocks
    private HarnessAgentServiceImpl service;

    @Test
    void remoteMcpChangeInvalidatesTheUsersRuntimeConfiguration() {
        AiModelRuntimeConfig model = new AiModelRuntimeConfig().setApiKey("private-model-key")
                .setMultimodalCapability(AgentModelMultimodalCapability.NONE);

        AgentRuntimeConfiguration first = configuration(model, "before");
        assertThat(configuration(model, "before")).isEqualTo(first);
        assertThat(configuration(model, "after")).isNotEqualTo(first);
        assertThat(first.toString()).doesNotContain("private-model-key");
    }

    @Test
    void modelCredentialChangeInvalidatesConfigurationWithoutKeepingTheSecret() {
        AiModelRuntimeConfig model = new AiModelRuntimeConfig().setApiKey("old-key");

        AgentRuntimeConfiguration first = configuration(model, "same-mcp");
        model.setApiKey("new-key");
        AgentRuntimeConfiguration second = configuration(model, "same-mcp");

        assertThat(second).isNotEqualTo(first);
        assertThat(second.toString()).doesNotContain("old-key", "new-key");
    }

    /**
     * 仅构建配置快照，避免启动模型连接或沙箱。
     */
    private AgentRuntimeConfiguration configuration(AiModelRuntimeConfig model, String mcpFingerprint) {
        return ReflectionTestUtils.invokeMethod(service, "runtimeConfiguration", model, new ResourceEnablement(Set.of()),
                new SkillRuntimeSnapshot(List.of(), "skills"), new McpRuntimeSnapshot(List.of(), mcpFingerprint));
    }
}
