package com.x.ai.tool;

import com.x.ai.service.AgentAppService;
import com.x.ai.service.AgentAppTemplateService;
import com.x.ai.common.domain.AgentAppDetailDo;
import com.x.ai.common.domain.AgentAppPackageDo;
import com.x.ai.common.dto.AgentAppSaveDto;
import com.x.ai.common.model.ToolContext;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.utils.JsonUtil;
import com.x.framework.common.utils.UserThreadLocalUtil;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentAppManagementToolsTest {

    private final AgentAppService applications = mock(AgentAppService.class);

    @AfterEach
    void clearIdentity() {
        UserThreadLocalUtil.clear();
    }

    @Test
    void exposesOnlyApplicationInputAndInjectsIdentityFromRuntimeContext() {
        Toolkit toolkit = toolkit();
        assertThat(toolkit.getToolNames()).contains("app_create", "app_get", "app_update", "app_list");
        String schemas = JsonUtil.toJsonStrOrThrow(toolkit.getToolSchemas().stream()
                .map(schema -> schema.getParameters()).toList());
        assertThat(schemas).doesNotContain("ToolContext", "\"context\"", "\"userId\"", "\"ownerUserId\"");
        assertThat(toolkit.getToolSchemas().stream().filter(schema -> schema.getName().equals("app_create"))
                .findFirst().orElseThrow().getParameters()).containsKey("properties");

        AgentAppDetailDo created = new AgentAppDetailDo().setContent(new AgentAppPackageDo().setSdkVersion(1)
                .setHtml("<button>创建</button>").setCss("button{color:red}")
                .setSource("const sourceMustNotBeReturned = 42;").setPermissions(List.of("storage")));
        created.setId("created-app").setName("会议纪要").setDescription("整理会议记录").setCurrentVersion("version-1");
        when(applications.create(eq("42"), any())).thenReturn(created);
        UserThreadLocalUtil.set(UserInfo.builder().userId("thread-user-must-not-be-used").build());
        RuntimeContext runtime = RuntimeContext.builder().userId("42").sessionId("conversation-1")
                .put(ToolContext.class, new ToolContext().setUserId("42").setModelId(7L)).build();

        ToolResultBlock result = create(toolkit, runtime);

        assertThat(result.getState()).isNotIn(ToolResultState.ERROR, ToolResultState.DENIED);
        ArgumentCaptor<AgentAppSaveDto> request = ArgumentCaptor.forClass(AgentAppSaveDto.class);
        verify(applications).create(eq("42"), request.capture());
        assertThat(request.getValue().getName()).isEqualTo("会议纪要");
        assertThat(request.getValue().getHtml()).isEqualTo("<main>会议记录</main>");
        assertThat(request.getValue().getSource()).isEqualTo("const notes: string = '待整理';");
        assertThat(request.getValue().getPermissions()).containsExactly("storage");
        String output = text(result);
        var summary = JsonUtil.OBJECT_MAPPER.readTree(output);
        assertThat(summary.path("id").asText()).isEqualTo("created-app");
        assertThat(summary.path("currentVersion").asText()).isEqualTo("version-1");
        assertThat(output).contains("会议纪要").doesNotContain("sourceMustNotBeReturned", "\"content\"", "\"source\"", "\"html\"");
    }

    @Test
    void missingInjectedToolContextCannotUseThreadIdentityToCreateAnApplication() {
        UserThreadLocalUtil.set(UserInfo.builder().userId("42").build());

        ToolResultBlock result = create(toolkit(), RuntimeContext.builder().userId("42").sessionId("conversation-1").build());

        assertThat(result.getState()).isEqualTo(ToolResultState.ERROR);
        verifyNoInteractions(applications);
    }

    /**
     * 使用生产相同的真实反射注册路径，避免直接调用方法漏掉参数注入问题。
     */
    private Toolkit toolkit() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new AgentAppManagementTools(applications, mock(AgentAppTemplateService.class)));
        return toolkit;
    }

    /**
     * 模拟模型发出的应用创建调用，仅连接内存工具与模拟业务服务。
     */
    private ToolResultBlock create(Toolkit toolkit, RuntimeContext runtime) {
        Map<String, Object> input = Map.of("request", Map.of("name", "会议纪要", "description", "整理会议记录",
                "html", "<main>会议记录</main>", "css", "main{padding:16px}",
                "source", "const notes: string = '待整理';", "permissions", List.of("storage")));
        ToolUseBlock call = ToolUseBlock.builder().id("create-app-call").name("app_create")
                .input(input).content(JsonUtil.toJsonStrOrThrow(input)).build();
        return toolkit.callTool(ToolCallParam.builder().toolUseBlock(call).runtimeContext(runtime).build())
                .block(Duration.ofSeconds(5));
    }

    /**
     * 读取模型实际收到的工具文本，验证摘要序列化没有重复暴露源码。
     */
    private String text(ToolResultBlock result) {
        return result.getOutput().stream().filter(TextBlock.class::isInstance).map(TextBlock.class::cast)
                .map(TextBlock::getText).collect(Collectors.joining());
    }
}
