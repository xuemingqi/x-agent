package com.x.ai.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.x.ai.service.AgentAppService;
import com.x.ai.common.domain.AgentAppDetailDo;
import com.x.ai.common.domain.AgentAppPackageDo;
import com.x.ai.common.dto.AgentAppDataSaveDto;
import com.x.ai.common.dto.AgentAppGenerateDto;
import com.x.ai.common.dto.AgentAppRunDto;
import com.x.ai.common.dto.AgentAppHttpRequestDto;
import com.x.ai.common.domain.HttpExecutionDo;
import com.x.ai.common.model.HttpRequestSpec;
import com.x.ai.common.model.AiModelRuntimeConfig;
import com.x.ai.common.model.McpRuntimeSnapshot;
import com.x.ai.db.entity.AgentAppData;
import com.x.ai.db.mapper.AgentAppDataMapper;
import com.x.ai.db.service.AgentAppDataIService;
import com.x.ai.db.service.impl.AgentAppDataIServiceImpl;
import com.x.ai.service.AiModelRuntimeService;
import com.x.ai.service.McpRuntimeService;
import com.x.ai.service.OutboundHttpService;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.JsonUtil;
import com.x.framework.common.utils.UserThreadLocalUtil;
import io.agentscope.core.model.transport.HttpRequest;
import io.agentscope.core.model.transport.HttpResponse;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.model.transport.HttpTransportFactory;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.validation.Validation;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentAppRuntimeServiceImplTest {

    private final AgentAppService applications = mock(AgentAppService.class);

    private final AiModelRuntimeService models = mock(AiModelRuntimeService.class);

    private final McpRuntimeService mcps = mock(McpRuntimeService.class);

    private final AgentAppDataIService data = mock(AgentAppDataIService.class);

    private final OutboundHttpService outboundHttp = mock(OutboundHttpService.class);

    private final HttpTransport transport = mock(HttpTransport.class);

    private HttpTransport originalTransport;

    private AgentAppRuntimeServiceImpl service;

    @BeforeAll
    static void initializeEntityMapping() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "app-data-test"),
                AgentAppData.class);
    }

    @BeforeEach
    void setUp() {
        service = new AgentAppRuntimeServiceImpl(applications, models, mcps, data, new Snowflake(1, 1), outboundHttp);
        originalTransport = HttpTransportFactory.getDefault();
        HttpTransportFactory.setDefault(transport);
        UserThreadLocalUtil.set(UserInfo.builder().userId("42").build());
        allow("model.generate", "agent.run", "storage");
        when(models.requireRuntimeModel("42", null)).thenReturn(new AiModelRuntimeConfig()
                .setModelId(7L).setModelName("app-test").setBaseUrl("https://model.example.test/v1")
                .setApiKey("private-test-key").setContextWindowSize(8192).setTemperature(0.5));
    }

    @AfterEach
    void tearDown() {
        HttpTransportFactory.setDefault(originalTransport);
        UserThreadLocalUtil.clear();
    }

    @Test
    void requiresOwnershipAndCurrentVersionBeforeAnyCapabilityExecution() {
        when(applications.get("42", "other-app")).thenThrow(new XException(ResponseCodeEnum.AGENT_APP_NOT_FOUND));
        assertError(() -> service.generate("other-app", generateRequest()), ResponseCodeEnum.AGENT_APP_NOT_FOUND);
        assertError(() -> service.generate("my-app", generateRequest().setVersion("old")),
                ResponseCodeEnum.AGENT_APP_VERSION_CONFLICT);
        assertError(() -> service.saveData("my-app", "old", "note", new AgentAppDataSaveDto()),
                ResponseCodeEnum.AGENT_APP_VERSION_CONFLICT);
        verifyNoInteractions(models, mcps, transport, data);
    }

    @Test
    void cannotCallCapabilitiesThatThePublishedVersionDidNotDeclare() {
        allow();
        assertError(() -> service.generate("my-app", generateRequest()), ResponseCodeEnum.AGENT_APP_PERMISSION_DENIED);
        assertError(() -> service.run("my-app", new AgentAppRunDto().setVersion("v1").setRequest("任务")),
                ResponseCodeEnum.AGENT_APP_PERMISSION_DENIED);
        assertError(() -> service.getData("my-app", "v1", "note"), ResponseCodeEnum.AGENT_APP_PERMISSION_DENIED);
        assertError(() -> service.requestHttp("my-app", new AgentAppHttpRequestDto().setVersion("v1")
                .setRequest(new HttpRequestSpec().setUrl("https://example.test"))), ResponseCodeEnum.AGENT_APP_PERMISSION_DENIED);
        verifyNoInteractions(outboundHttp);
        verifyNoInteractions(models, mcps, transport, data);
    }

    @Test
    void httpCapabilityForwardsOnlyTheExplicitRequestAndPreservesCancellation() {
        allow("http.request");
        HttpRequestSpec request = new HttpRequestSpec().setUrl("https://example.test");
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        when(outboundHttp.execute(request)).thenReturn(Mono.<HttpExecutionDo>never().doOnCancel(() -> cancelled.set(true)));

        var subscription = service.requestHttp("my-app", new AgentAppHttpRequestDto().setVersion("v1")
                .setRequest(request)).subscribe();
        subscription.dispose();

        verify(outboundHttp).execute(request);
        assertThat(cancelled).isTrue();
        assertError(() -> service.requestHttp("my-app", new AgentAppHttpRequestDto().setVersion("old").setRequest(request)),
                ResponseCodeEnum.AGENT_APP_VERSION_CONFLICT);
    }

    @Test
    void generatesWithTheOwnedModelWithoutPlatformToolsOrSharedHistory() {
        when(transport.execute(any())).thenReturn(response("第一次结果"), response("第二次结果"));

        assertThat(service.generate("my-app", generateRequest().setSystemPrompt("生成纪要")).getText()).isEqualTo("第一次结果");
        assertThat(service.generate("my-app", generateRequest().setPrompt("另一份输入")).getText()).isEqualTo("第二次结果");

        verify(models, times(2)).requireRuntimeModel("42", null);
        verifyNoInteractions(mcps);
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(transport, times(2)).execute(requests.capture());
        assertThat(requests.getAllValues().getFirst().getBody()).contains("生成纪要", "app-test", "第一份输入")
                .doesNotContain("skill_manage", "app_create", "shell", "filesystem", "private-test-key");
        assertThat(requests.getAllValues().getLast().getBody()).contains("另一份输入").doesNotContain("第一份输入", "第一次结果");
    }

    @Test
    void preservesModelOwnershipRejectionBeforeConnectingToMcp() {
        when(models.requireRuntimeModel("42", 99L)).thenThrow(new XException(ResponseCodeEnum.AGENT_MODEL_UNAVAILABLE));
        assertError(() -> service.run("my-app", new AgentAppRunDto().setVersion("v1").setRequest("任务").setModelId(99L)),
                ResponseCodeEnum.AGENT_MODEL_UNAVAILABLE);
        verifyNoInteractions(mcps, transport);
    }

    @Test
    void runsOnlyWithTheCurrentUsersMcpAndReleasesTheConnectionAfterward() {
        McpClientWrapper client = client("my-tools");
        when(client.listTools()).thenReturn(Mono.just(List.of(McpSchema.Tool.builder().name("lookup_weather")
                .description("查看天气").inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(),
                        false, null, null)).build())));
        enableClients(client);
        when(transport.execute(any())).thenReturn(response("晴天"));

        var result = service.run("my-app", new AgentAppRunDto().setVersion("v1").setRequest("查询天气"));

        assertThat(result.getText()).isEqualTo("晴天");
        assertThat(result.getSessionId()).startsWith("app:my-app:");
        verify(mcps).load("42");
        verify(client).close();
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(transport).execute(request.capture());
        assertThat(request.getValue().getBody()).contains("lookup_weather")
                .doesNotContain("skill_manage", "app_create", "mcp_server_create", "schedule_task_create", "filesystem", "shell");
    }

    @Test
    void closesRegisteredFailedAndUnstartedMcpClientsExactlyOnceOnRegistrationFailure() {
        McpClientWrapper first = client("first");
        McpClientWrapper failed = client("failed");
        McpClientWrapper unstarted = client("unstarted");
        when(failed.listTools()).thenReturn(Mono.error(new IllegalStateException("private-upstream-detail")));
        enableClients(first, failed, unstarted);

        assertError(() -> service.run("my-app", new AgentAppRunDto().setVersion("v1").setRequest("任务")),
                ResponseCodeEnum.AGENT_APP_EXECUTION_ERROR);

        verify(first).close();
        verify(failed).close();
        verify(unstarted).close();
        verify(unstarted, never()).initialize();
        verifyNoInteractions(transport);
    }

    @Test
    void releasesMcpClientsAndReturnsOnlyTheApplicationErrorWhenTheModelHasNoResult() {
        McpClientWrapper client = client("my-tools");
        enableClients(client);
        when(transport.execute(any())).thenReturn(response(""));

        assertError(() -> service.run("my-app", new AgentAppRunDto().setVersion("v1").setRequest("任务")),
                ResponseCodeEnum.AGENT_APP_EXECUTION_ERROR);

        verify(client).close();
    }

    @Test
    void scopesReadsAndDeletesToBothTheCurrentUserAndApplication() {
        AgentAppDataMapper mapper = mock(AgentAppDataMapper.class);
        AgentAppDataIServiceImpl persistentData = new AgentAppDataIServiceImpl();
        ReflectionTestUtils.setField(persistentData, "baseMapper", mapper);
        ReflectionTestUtils.setField(persistentData, "entityClass", AgentAppData.class);
        service = new AgentAppRuntimeServiceImpl(applications, models, mcps, persistentData, new Snowflake(1, 1), outboundHttp);

        assertThat(service.getData("my-app", "v1", "meeting.notes")).isNull();
        assertThat(service.deleteData("my-app", "v1", "meeting.notes")).isTrue();

        ArgumentCaptor<LambdaQueryWrapper<AgentAppData>> read = ArgumentCaptor.captor();
        verify(mapper).selectOne(read.capture());
        assertThat(read.getValue().getSqlSegment()).contains("owner_user_id =", "app_id =", "data_key =");
        assertThat(read.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder("42", "my-app", "meeting.notes");
        ArgumentCaptor<LambdaUpdateWrapper<AgentAppData>> deletion = ArgumentCaptor.captor();
        verify(mapper).delete(deletion.capture());
        assertThat(deletion.getValue().getSqlSegment()).contains("owner_user_id =", "app_id =", "data_key =");
        assertThat(deletion.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder("42", "my-app", "meeting.notes");
    }

    @Test
    void writesOnlyCallerOwnedJsonValuesAndRejectsOversizedUtf8OrInvalidKeys() {
        var value = JsonUtil.OBJECT_MAPPER.valueToTree(Map.of("notes", "会议纪要"));
        assertThat(service.saveData("my-app", "v1", "meeting.notes", new AgentAppDataSaveDto().setValue(value))).isTrue();
        ArgumentCaptor<AgentAppData> saved = ArgumentCaptor.forClass(AgentAppData.class);
        verify(data).upsert(saved.capture());
        assertThat(saved.getValue().getOwnerUserId()).isEqualTo("42");
        assertThat(saved.getValue().getAppId()).isEqualTo("my-app");
        assertThat(saved.getValue().getDataKey()).isEqualTo("meeting.notes");
        assertThat(saved.getValue().getValueJson()).contains("会议纪要");

        assertError(() -> service.saveData("my-app", "v1", "../outside/key", new AgentAppDataSaveDto()),
                ResponseCodeEnum.AGENT_APP_DATA_INVALID);
        var oversized = JsonUtil.OBJECT_MAPPER.valueToTree("字".repeat(350_000));
        assertError(() -> service.saveData("my-app", "v1", "large", new AgentAppDataSaveDto().setValue(oversized)),
                ResponseCodeEnum.AGENT_APP_DATA_INVALID);
        verify(data, times(1)).upsert(any());
    }

    @Test
    void rejectsUriDotSegmentsWhilePreservingOtherSupportedKeyPrefixes() {
        for (String key : List.of(".", "..")) {
            assertError(() -> service.saveData("my-app", "v1", key, new AgentAppDataSaveDto()),
                    ResponseCodeEnum.AGENT_APP_DATA_INVALID);
            assertError(() -> service.getData("my-app", "v1", key), ResponseCodeEnum.AGENT_APP_DATA_INVALID);
            assertError(() -> service.deleteData("my-app", "v1", key), ResponseCodeEnum.AGENT_APP_DATA_INVALID);
        }
        verifyNoInteractions(data);

        for (String key : List.of("_state", ".state", "-state")) {
            assertThat(service.saveData("my-app", "v1", key, new AgentAppDataSaveDto())).isTrue();
        }
        ArgumentCaptor<AgentAppData> saved = ArgumentCaptor.forClass(AgentAppData.class);
        verify(data, times(3)).upsert(saved.capture());
        assertThat(saved.getAllValues()).extracting(AgentAppData::getDataKey)
                .containsExactly("_state", ".state", "-state");
    }

    @Test
    void validatesVersionPromptAndRequestSizesAtTheHttpBoundary() {
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            var validator = validation.getValidator();
            assertThat(validator.validate(new AgentAppGenerateDto().setVersion("").setPrompt("x".repeat(100_001))))
                    .hasSize(2);
            assertThat(validator.validate(new AgentAppRunDto().setVersion("v1").setRequest(""))).hasSize(1);
        }
    }

    /**
     * 固定当前用户的发布版本，仅替换本用例允许的能力。
     */
    private void allow(String... permissions) {
        AgentAppDetailDo detail = new AgentAppDetailDo().setContent(new AgentAppPackageDo()
                .setSdkVersion(1).setPermissions(List.of(permissions)));
        detail.setId("my-app").setCurrentVersion("v1");
        when(applications.get("42", "my-app")).thenReturn(detail);
    }

    /**
     * 提供符合当前应用版本的最小生成请求。
     */
    private AgentAppGenerateDto generateRequest() {
        return new AgentAppGenerateDto().setVersion("v1").setPrompt("第一份输入");
    }

    /**
     * 仅为当前用户提供本用例的 MCP 客户端快照。
     */
    private void enableClients(McpClientWrapper... clients) {
        McpRuntimeSnapshot snapshot = new McpRuntimeSnapshot(List.of(), "user-42-mcp");
        when(mcps.load("42")).thenReturn(snapshot);
        when(mcps.getClients(snapshot)).thenReturn(List.of(clients));
    }

    /**
     * 创建不访问网络的 MCP 连接以验证注册与资源释放。
     */
    private McpClientWrapper client(String name) {
        McpClientWrapper client = mock(McpClientWrapper.class);
        when(client.getName()).thenReturn(name);
        when(client.initialize()).thenReturn(Mono.empty());
        when(client.listTools()).thenReturn(Mono.just(List.of()));
        return client;
    }

    /**
     * 模拟模型提供方的文本结果，不依赖实际网关。
     */
    private HttpResponse response(String text) {
        return HttpResponse.builder().statusCode(200).body(JsonUtil.toJsonStrOrThrow(Map.of("id", "result",
                "model", "app-test", "object", "chat.completion", "choices", List.of(Map.of("index", 0,
                        "finish_reason", "stop", "message", Map.of("role", "assistant", "content", text)))))).build();
    }

    /**
     * 断言调用只通过约定业务错误向应用返回失败。
     */
    private void assertError(Runnable action, ResponseCodeEnum code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(XException.class,
                error -> assertThat(error.getResponseCodeEnum()).isEqualTo(code));
    }
}
