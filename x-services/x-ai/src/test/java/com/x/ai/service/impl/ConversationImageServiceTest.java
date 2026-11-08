package com.x.ai.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.x.ai.common.domain.ConversationFileDo;
import com.x.ai.common.dto.BrowserScreenshotResultDto;
import com.x.ai.common.model.BrowserScreenshotContext;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.ai.controller.ConversationFileController;
import com.x.ai.db.entity.AgentConversationFile;
import com.x.ai.db.enums.AgentConversationFileKind;
import com.x.ai.db.mapper.AgentConversationFileMapper;
import com.x.ai.db.service.AgentConversationFileIService;
import com.x.framework.common.exception.XException;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.minio.property.MinioProperties;
import com.x.ai.properties.AgentProperties;
import com.x.ai.properties.BrowserScreenshotProperties;
import com.x.ai.properties.MultimodalProperties;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventEmitter;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.URLSource;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.model.FileDownloadResponse;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import okhttp3.Headers;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationImageServiceTest {

    @AfterEach
    void clearIdentity() {
        UserThreadLocalUtil.clear();
    }

    private static final String PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aG9sAAAAASUVORK5CYII=";

    private static final String SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
              <circle cx="50" cy="50" r="40" fill="orange"/>
            </svg>
            """;

    private final AgentConversationFileIService database = mock(AgentConversationFileIService.class);

    private final AgentConversationFileMapper mapper = mock(AgentConversationFileMapper.class);

    private final MinioClient storage = mock(MinioClient.class);

    private final MultimodalProperties properties = new MultimodalProperties();

    private final List<AgentConversationFile> saved = new ArrayList<>();

    private ConversationFileServiceImpl service;

    @BeforeAll
    static void initializeEntityMappings() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "conversation-image-test"),
                AgentConversationFile.class);
    }

    @BeforeEach
    void setUp() {
        UserThreadLocalUtil.set(UserInfo.builder().userId("user-1").build());
        when(mapper.selectList(any())).thenAnswer(invocation -> saved);
        when(database.lambdaQuery()).thenAnswer(invocation -> new LambdaQueryChainWrapper<>(mapper));
        when(database.save(any())).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return true;
        });
        when(database.bindPendingInputs(any(), any(), any(), any())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(2);
            Long messageId = invocation.getArgument(3);
            saved.stream().filter(file -> ids.contains(file.getId())).forEach(file -> file.setMessageId(messageId));
            return ids.size();
        });
        service = new ConversationFileServiceImpl(database, new AgentProperties(), properties,
                new MinioProperties(), storage, new Snowflake(1, 1));
    }

    @Test
    void readsOwnedImageAsBase64AndLeavesDocumentsForSandboxTools() throws Exception {
        AgentConversationFile file = new AgentConversationFile().setId(1L).setUserId("user-1")
                .setSessionId("session-1").setMessageId(2L).setKind(AgentConversationFileKind.INPUT)
                .setFileName("photo.png").setObjectKey("private/photo.png");
        when(database.getById(1L)).thenReturn(file);
        when(storage.getObject(any(GetObjectArgs.class))).thenAnswer(ignored -> new GetObjectResponse(
                new Headers.Builder().build(), "bucket", "region", "private/photo.png",
                new ByteArrayInputStream(Base64.getDecoder().decode(PNG))));

        List<ContentBlock> content = service.readInputImages("user-1", "session-1", List.of(
                new ConversationFileDo().setId("1").setName("photo.png"),
                new ConversationFileDo().setId("2").setName("report.pdf")));

        assertThat(content).hasSize(1);
        Base64Source image = (Base64Source) ((DataBlock) content.getFirst()).getSource();
        assertThat(image.getMediaType()).isEqualTo("image/png");
        assertThat(image.getData()).isEqualTo(PNG);
        verify(database, never()).getById(2L);
    }

    @Test
    void returnsAuthorizedScreenshotThroughRealAttachmentBindingAndImageRead() throws Exception {
        AgentConversationFile preview = new AgentConversationFile().setId(20L).setUserId("user-1")
                .setSessionId("session-1").setMessageId(10L).setKind(AgentConversationFileKind.OUTPUT)
                .setFileName("page.html").setContentType("text/html").setSizeBytes(100L);
        // 发布查询返回 HTML，后续附件绑定查询返回实际上传的截图。
        when(mapper.selectList(any())).thenReturn(List.of(preview)).thenReturn(saved);
        when(database.getById(any())).thenAnswer(invocation -> saved.stream()
                .filter(file -> file.getId().equals(invocation.getArgument(0))).findFirst().orElse(null));
        when(storage.getObject(any(GetObjectArgs.class))).thenAnswer(ignored -> new GetObjectResponse(
                new Headers.Builder().build(), "bucket", "region", "screenshot.png",
                new ByteArrayInputStream(Base64.getDecoder().decode(PNG))));
        BrowserScreenshotServiceImpl screenshots = new BrowserScreenshotServiceImpl(service,
                new BrowserScreenshotProperties());
        List<AgentEvent> events = new ArrayList<>();
        RuntimeContext context = RuntimeContext.builder().userId("user-1").sessionId("session-1")
                .put(BrowserScreenshotContext.class, new BrowserScreenshotContext().setTaskId("task-1").setMessageId(10L))
                .build();
        var result = screenshots.request("20", "检查棋盘布局", context)
                .contextWrite(value -> value.put(AgentEventEmitter.CONTEXT_KEY, (AgentEventEmitter) events::add)).toFuture();
        String requestId = (String) ((CustomEvent) events.getFirst()).getValue().get("requestId");
        ConversationFileDo uploaded = service.upload("session-1", List.of(new MockMultipartFile(
                "files", "screenshot.png", "image/png", Base64.getDecoder().decode(PNG)))).getFirst();

        screenshots.complete("task-1", requestId,
                new BrowserScreenshotResultDto().setAccepted(true).setFileId(Long.valueOf(uploaded.getId())));

        DataBlock image = result.get(1, TimeUnit.SECONDS).getOutput().stream().filter(DataBlock.class::isInstance)
                .map(DataBlock.class::cast).findFirst().orElseThrow();
        assertThat(((Base64Source) image.getSource()).getData()).isEqualTo(PNG);
        assertThat(saved.getFirst().getMessageId()).isEqualTo(10L);
        assertThat(((CustomEvent) events.getLast()).getValue()).containsEntry("status", "received");
        verify(database).bindPendingInputs("user-1", "session-1", List.of(Long.valueOf(uploaded.getId())), 10L);
    }

    @Test
    void rejectsNullOrDuplicateAttachmentIdsBeforeBinding() {
        assertThatThrownBy(() -> service.prepareInputs("user-1", "session-1", Arrays.asList(1L, null), 10L))
                .isInstanceOf(XException.class);
        assertThatThrownBy(() -> service.prepareInputs("user-1", "session-1", List.of(1L, 1L), 10L))
                .isInstanceOf(XException.class);
        verify(database, never()).bindPendingInputs(any(), any(), any(), any());
    }

    @Test
    void rejectsSelectedAttachmentsWithoutAnOwningMessage() {
        assertThatThrownBy(() -> service.prepareInputs("user-1", "session-1", List.of(1L), null))
                .isInstanceOfSatisfying(XException.class, error -> assertThat(error.getResponseCodeEnum())
                        .isEqualTo(ResponseCodeEnum.AGENT_CONVERSATION_FILE_INVALID));
        verify(database, never()).lambdaQuery();
        verify(database, never()).bindPendingInputs(any(), any(), any(), any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = 10L)
    void scopesPublishedOutputsToTheUserSessionAndNullableMessage(Long messageId) {
        assertThat(service.listPublishedOutputs("user-1", "session-1", messageId)).isEmpty();

        ArgumentCaptor<LambdaQueryWrapper<AgentConversationFile>> query = ArgumentCaptor.captor();
        verify(mapper).selectList(query.capture());
        LambdaQueryWrapper<AgentConversationFile> condition = query.getValue();
        String sql = condition.getSqlSegment();
        assertThat(sql).contains("user_id =", "session_id =", "kind =");
        if (messageId == null) {
            assertThat(sql).contains("message_id IS NULL").doesNotContain("message_id =");
            assertThat(condition.getParamNameValuePairs().values())
                    .containsExactlyInAnyOrder("user-1", "session-1", AgentConversationFileKind.OUTPUT);
        } else {
            assertThat(sql).contains("message_id =").doesNotContain("message_id IS NULL");
            assertThat(condition.getParamNameValuePairs().values())
                    .containsExactlyInAnyOrder("user-1", "session-1", AgentConversationFileKind.OUTPUT, messageId);
        }
    }

    @Test
    void rejectsTheWholeBindingWhenAnotherRequestClaimsOneOfTheAttachments() {
        AgentConversationFile first = new AgentConversationFile().setId(1L).setUserId("user-1")
                .setSessionId("session-1").setKind(AgentConversationFileKind.INPUT);
        AgentConversationFile second = new AgentConversationFile().setId(2L).setUserId("user-1")
                .setSessionId("session-1").setKind(AgentConversationFileKind.INPUT);
        saved.addAll(List.of(first, second));
        doReturn(1).when(database).bindPendingInputs("user-1", "session-1", List.of(1L, 2L), 10L);

        assertThatThrownBy(() -> service.prepareInputs("user-1", "session-1", List.of(1L, 2L), 10L))
                .isInstanceOfSatisfying(XException.class, error -> assertThat(error.getResponseCodeEnum())
                        .isEqualTo(ResponseCodeEnum.AGENT_CONVERSATION_FILE_INVALID));
        verify(database).bindPendingInputs("user-1", "session-1", List.of(1L, 2L), 10L);
    }

    @Test
    void rejectsImagesFromAnotherUserBeforeReadingStorage() throws Exception {
        when(database.getById(1L)).thenReturn(new AgentConversationFile().setId(1L)
                .setUserId("another-user").setSessionId("session-1"));

        assertThatThrownBy(() -> service.readInputImages("user-1", "session-1",
                List.of(new ConversationFileDo().setId("1").setName("image.png"))))
                .isInstanceOf(XException.class);
        verify(storage, never()).getObject(any(GetObjectArgs.class));
    }

    @Test
    void persistsImageOnlyRepliesOnceAndKeepsTheirUserMessageAssociation() throws Exception {
        RuntimeContext context = RuntimeContext.builder().userId("user-1").sessionId("session-1").build();
        Msg response = new AssistantMessage(DataBlock.builder()
                .source(URLSource.builder().url("data:image/png;base64," + PNG).mimeType("image/png").build()).build());

        service.publishImages(context, 10L, response);
        service.publishImages(context, 10L, response);

        assertThat(saved).hasSize(1);
        AgentConversationFile file = saved.getFirst();
        assertThat(file.getUserId()).isEqualTo("user-1");
        assertThat(file.getSessionId()).isEqualTo("session-1");
        assertThat(file.getMessageId()).isEqualTo(10L);
        assertThat(file.getKind()).isEqualTo(AgentConversationFileKind.OUTPUT);
        assertThat(file.getContentType()).isEqualTo("image/png");
        ArgumentCaptor<PutObjectArgs> upload = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(storage, times(1)).putObject(upload.capture());
        assertThat(upload.getValue().object()).contains("user-1/session-1/");
    }

    @Test
    void refusesUntrustedRemoteImagesAndOversizedBase64() {
        RuntimeContext context = RuntimeContext.builder().userId("user-1").sessionId("session-1").build();
        Msg remote = new AssistantMessage(DataBlock.builder().source(URLSource.builder()
                .url("http://127.0.0.1/private").mimeType("image/png").build()).build());
        assertThatThrownBy(() -> service.publishImages(context, 1L, remote)).isInstanceOf(XException.class);

        properties.setMaxImageSizeBytes(12);
        Msg large = new AssistantMessage(DataBlock.builder().source(Base64Source.builder()
                .mediaType("image/png").data(PNG).build()).build());
        assertThatThrownBy(() -> service.publishImages(context, 1L, large)).isInstanceOf(XException.class);
        assertThat(saved).isEmpty();
    }

    @Test
    void detectsActualImageMimeTypeAndRejectsDisguisedUploads() throws Exception {
        List<ConversationFileDo> files = service.upload("session-1", List.of(new MockMultipartFile(
                "files", "photo.jpg", "application/octet-stream", Base64.getDecoder().decode(PNG))));
        assertThat(files.getFirst().getContentType()).isEqualTo("image/png");

        assertThatThrownBy(() -> service.upload("session-1", List.of(new MockMultipartFile(
                "files", "bad.png", "image/png", "<html>not a picture</html>".getBytes()))))
                .isInstanceOf(XException.class);
        verify(storage, times(1)).putObject(any(PutObjectArgs.class));
    }

    @Test
    void storesUploadedSvgWithItsPreviewMimeType() throws Exception {
        List<ConversationFileDo> files = service.upload("session-1", List.of(new MockMultipartFile(
                "files", "drawing.svg", "application/octet-stream", SVG.getBytes(StandardCharsets.UTF_8))));

        assertThat(files.getFirst().getContentType()).isEqualTo("image/svg+xml");
        assertThat(saved.getFirst().getContentType()).isEqualTo("image/svg+xml");
        ArgumentCaptor<PutObjectArgs> upload = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(storage).putObject(upload.capture());
        assertThat(upload.getValue().contentType().toString()).isEqualTo("image/svg+xml");
        assertThat(service.readInputImages("user-1", "session-1", files)).isEmpty();
    }

    @Test
    void publishesGeneratedSvgWithItsPreviewMimeType() throws Exception {
        AbstractFilesystem filesystem = mock(AbstractFilesystem.class);
        RuntimeContext context = RuntimeContext.builder().userId("user-1").sessionId("session-1")
                .put(AbstractFilesystem.class, filesystem).build();
        String path = "conversation-files/session-1/outputs/drawing.svg";
        when(filesystem.downloadFiles(context, List.of(path)))
                .thenReturn(List.of(FileDownloadResponse.success(path, SVG.getBytes(StandardCharsets.UTF_8))));

        ConversationFileDo file = service.publish(context, 10L, path, "drawing.svg");

        assertThat(file.getContentType()).isEqualTo("image/svg+xml");
        assertThat(saved.getFirst().getContentType()).isEqualTo("image/svg+xml");
        ArgumentCaptor<PutObjectArgs> upload = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(storage).putObject(upload.capture());
        assertThat(upload.getValue().contentType().toString()).isEqualTo("image/svg+xml");
    }

    @ParameterizedTest
    @CsvSource({
            "drawing.svg, image/svg+xml",
            "DRAWING.SVG, image/svg+xml",
            "photo.png, image/png",
            "photo.jpg, image/png",
            "archive.bin, application/octet-stream"
    })
    void preservesSavedContentTypesForListingAndDownload(String fileName, String contentType) throws Exception {
        byte[] content = MediaType.IMAGE_PNG_VALUE.equals(contentType)
                ? Base64.getDecoder().decode(PNG) : SVG.getBytes(StandardCharsets.UTF_8);
        AgentConversationFile file = new AgentConversationFile().setId(1L).setUserId("user-1")
                .setSessionId("session-1").setFileName(fileName).setContentType(contentType)
                .setObjectKey("private/stored-file").setSizeBytes((long) content.length);
        saved.add(file);
        when(database.getById(1L)).thenReturn(file);
        when(storage.getObject(any(GetObjectArgs.class))).thenAnswer(ignored -> new GetObjectResponse(
                new Headers.Builder().build(), "bucket", "region", file.getObjectKey(),
                new ByteArrayInputStream(content)));

        ResponseEntity<Resource> response = new ConversationFileController(service).download("session-1", 1L);

        assertThat(service.list("user-1", "session-1").getFirst().getContentType()).isEqualTo(contentType);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType(contentType));
        assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        try (InputStream input = response.getBody().getInputStream()) {
            assertThat(input.readAllBytes()).isEqualTo(content);
        }
        assertThat(file.getContentType()).isEqualTo(contentType);
    }
}
