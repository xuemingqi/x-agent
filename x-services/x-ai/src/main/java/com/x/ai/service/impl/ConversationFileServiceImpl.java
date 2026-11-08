package com.x.ai.service.impl;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.text.StrPool;
import cn.hutool.crypto.digest.DigestUtil;
import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.ai.common.constants.AgentConstant;
import com.x.ai.common.domain.ConversationFileDo;
import com.x.ai.common.domain.ConversationFileDownloadDo;
import com.x.ai.db.entity.AgentConversationFile;
import com.x.ai.db.enums.AgentConversationFileKind;
import com.x.ai.db.service.AgentConversationFileIService;
import com.x.framework.common.annotation.LogIgnore;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.ImageUtil;
import com.x.framework.common.utils.StringUtil;
import com.x.framework.minio.property.MinioProperties;
import com.x.ai.properties.AgentProperties;
import com.x.ai.properties.MultimodalProperties;
import com.x.ai.service.ConversationFileService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.Source;
import io.agentscope.core.message.URLSource;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.model.FileDownloadResponse;
import io.agentscope.harness.agent.filesystem.model.FileUploadResponse;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationFileServiceImpl implements ConversationFileService {

    private final AgentConversationFileIService agentConversationFileIService;

    private final AgentProperties agentProperties;

    private final MultimodalProperties multimodalProperties;

    private final MinioProperties minioProperties;

    private final MinioClient minioClient;

    private final Snowflake snowflake;


    @LogIgnore
    @Override
    public List<ConversationFileDo> upload(String sessionId, List<MultipartFile> files) {
        String userId = UserThreadLocalUtil.getUserId();

        List<AgentConversationFile> uploaded = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                uploaded.add(uploadFile(userId, sessionId, file));
            }
            return uploaded.stream().map(this::toFile).toList();
        } catch (RuntimeException error) {
            rollbackUploadedFiles(uploaded, error);
            throw error;
        }
    }

    @LogIgnore
    @Override
    public ConversationFileDownloadDo download(String sessionId, Long fileId) {
        String userId = UserThreadLocalUtil.getUserId();
        AgentConversationFile file = requireFile(userId, sessionId, fileId);
        try {
            GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(file.getObjectKey())
                    .build());
            return new ConversationFileDownloadDo()
                    .setFileName(file.getFileName())
                    .setContentType(file.getContentType())
                    .setSizeBytes(file.getSizeBytes())
                    .setResource(new InputStreamResource(response));
        } catch (Exception error) {
            throw storageError("download", file.getObjectKey(), error);
        }
    }

    @Override
    public ConversationFileDo getFile(String userId, String sessionId, Long fileId) {
        return toFile(requireFile(userId, sessionId, fileId));
    }

    @Override
    public boolean deletePending(String sessionId, Long fileId) {
        String userId = UserThreadLocalUtil.getUserId();
        AgentConversationFile file = requireFile(userId, sessionId, fileId);
        if (file.getMessageId() != null || file.getKind() != AgentConversationFileKind.INPUT) {
            throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_INVALID);
        }
        removeObject(file.getObjectKey());
        return agentConversationFileIService.removeById(file.getId());
    }

    @Override
    public List<ConversationFileDo> list(String userId, String sessionId) {
        return agentConversationFileIService.lambdaQuery()
                .eq(AgentConversationFile::getUserId, userId)
                .eq(AgentConversationFile::getSessionId, sessionId)
                .orderByAsc(AgentConversationFile::getId)
                .list()
                .stream()
                .map(this::toFile)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ConversationFileDo> prepareInputs(String userId, String sessionId, List<Long> attachmentIds,
                                                  Long userMessageId) {
        if (CollectionUtils.isEmpty(attachmentIds)) {
            return List.of();
        }
        if (userMessageId == null
                || attachmentIds.size() > agentProperties.getConversationFile().getMaxFilesPerMessage()
                || attachmentIds.stream().anyMatch(Objects::isNull)
                || new LinkedHashSet<>(attachmentIds).size() != attachmentIds.size()) {
            throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_INVALID);
        }
        return bindSelectedFiles(userId, sessionId, attachmentIds, userMessageId)
                .stream()
                .map(this::toFile)
                .toList();
    }

    @LogIgnore
    @Override
    public List<ContentBlock> readInputImages(String userId, String sessionId, List<ConversationFileDo> attachments) {
        return attachments.stream()
                .filter(file -> ImageUtil.contentType(file.getName()) != null)
                .map(file -> readInputImage(userId, sessionId, Long.valueOf(file.getId())))
                .toList();
    }

    @LogIgnore
    @Override
    public void publishImages(RuntimeContext context, Long userMessageId, Msg result) {
        if (result == null) {
            return;
        }
        List<Source> sources = result.getContent().stream()
                .map(block -> switch (block) {
                    case DataBlock data -> data.getSource();
                    case ImageBlock image -> image.getSource();
                    default -> null;
                })
                .filter(source -> source instanceof Base64Source base64 && base64.getMediaType() != null
                        && base64.getMediaType().startsWith("image/")
                        || source instanceof URLSource url && isImageSource(url))
                .toList();
        if (sources.isEmpty()) {
            return;
        }
        if (sources.size() > agentProperties.getConversationFile().getMaxFilesPerMessage()) {
            throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_INVALID);
        }
        List<ConversationFileDo> published = new ArrayList<>(
                listPublishedOutputs(context.getUserId(), context.getSessionId(), userMessageId));
        for (Source source : sources) {
            byte[] content = readImageSource(source);
            String contentType = requireImageType(content);
            String fileName = "image-" + DigestUtil.sha256Hex(content) + "." + ImageUtil.extension(contentType);
            String path = String.join(StrPool.SLASH, AgentConstant.CONVERSATION_FILE_WORKSPACE_DIRECTORY,
                    context.getSessionId(), AgentConstant.CONVERSATION_FILE_OUTPUT_DIRECTORY,
                    String.valueOf(userMessageId), fileName);
            if (published.stream().noneMatch(file -> path.equals(file.getWorkspacePath()))) {
                published.add(saveOutput(context, userMessageId, path, fileName, content, contentType));
            }
        }
    }

    @Override
    public void materializeFiles(RuntimeContext context) {
        AbstractFilesystem filesystem = requireFilesystem(context);
        List<AgentConversationFile> files = agentConversationFileIService.lambdaQuery()
                .eq(AgentConversationFile::getUserId, context.getUserId())
                .eq(AgentConversationFile::getSessionId, context.getSessionId())
                .isNotNull(AgentConversationFile::getMessageId)
                .orderByAsc(AgentConversationFile::getId)
                .list();
        files.forEach(file -> materialize(file, filesystem, context));
    }

    @Override
    public List<ConversationFileDo> listPublishedOutputs(String userId, String sessionId, Long userMessageId) {
        return agentConversationFileIService.lambdaQuery()
                .eq(AgentConversationFile::getUserId, userId)
                .eq(AgentConversationFile::getSessionId, sessionId)
                .eq(userMessageId != null, AgentConversationFile::getMessageId, userMessageId)
                .isNull(userMessageId == null, AgentConversationFile::getMessageId)
                .eq(AgentConversationFile::getKind, AgentConversationFileKind.OUTPUT)
                .orderByAsc(AgentConversationFile::getId)
                .list()
                .stream()
                .map(this::toFile)
                .toList();
    }

    @Override
    public Map<String, List<ConversationFileDo>> listPublishedOutputs(String userId, Collection<String> sessionIds) {
        if (StringUtil.isBlank(userId) || CollectionUtils.isEmpty(sessionIds)) {
            return Map.of();
        }
        return agentConversationFileIService.lambdaQuery()
                .eq(AgentConversationFile::getUserId, userId)
                .in(AgentConversationFile::getSessionId, sessionIds)
                .eq(AgentConversationFile::getKind, AgentConversationFileKind.OUTPUT)
                .orderByAsc(AgentConversationFile::getId)
                .list()
                .stream()
                .map(this::toFile)
                .collect(Collectors.groupingBy(ConversationFileDo::getSessionId, LinkedHashMap::new, Collectors.toList()));
    }

    @Override
    public ConversationFileDo publish(RuntimeContext context, Long userMessageId, String sandboxPath, String displayName) {
        String sessionId = context.getSessionId();
        String normalizedPath = validateOutputPath(sessionId, sandboxPath);
        AbstractFilesystem filesystem = requireFilesystem(context);
        FileDownloadResponse downloaded = filesystem
                .downloadFiles(context, List.of(normalizedPath))
                .getFirst();
        if (!downloaded.isSuccess() || downloaded.content() == null) {
            throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_NOT_FOUND);
        }
        byte[] content = downloaded.content();
        validateFileSize(content.length);

        String fileName = safeFileName(StringUtil.isBlank(displayName)
                ? Path.of(normalizedPath).getFileName().toString() : displayName);
        String contentType = detectContentType(fileName);
        if (ImageUtil.contentType(fileName) != null) {
            contentType = requireImageType(content);
        }
        return saveOutput(context, userMessageId, normalizedPath, fileName, content, contentType);
    }

    /**
     * 沙箱产物和模型图片共用存储、授权和会话归属。
     */
    private ConversationFileDo saveOutput(RuntimeContext context, Long userMessageId, String path, String fileName,
                                          byte[] content, String contentType) {
        String userId = context.getUserId();
        String sessionId = context.getSessionId();
        long fileId = snowflake.nextId();
        String objectKey = objectKey(userId, sessionId, fileId, fileName);
        putObject(objectKey, contentType, new ByteArrayInputStream(content), content.length);

        AgentConversationFile file = new AgentConversationFile()
                .setId(fileId)
                .setMessageId(userMessageId)
                .setSessionId(sessionId)
                .setUserId(userId)
                .setKind(AgentConversationFileKind.OUTPUT)
                .setFileName(fileName)
                .setObjectKey(objectKey)
                .setSandboxPath(path)
                .setContentType(contentType)
                .setSizeBytes((long) content.length);
        saveMetadataOrRemoveObject(file);

        return toFile(file);
    }

    @Override
    public void deleteConversationFiles(String userId, String sessionId) {
        List<AgentConversationFile> files = agentConversationFileIService.lambdaQuery()
                .eq(AgentConversationFile::getUserId, userId)
                .eq(AgentConversationFile::getSessionId, sessionId)
                .list();
        files.forEach(file -> removeObject(file.getObjectKey()));
        agentConversationFileIService.lambdaUpdate()
                .eq(AgentConversationFile::getUserId, userId)
                .eq(AgentConversationFile::getSessionId, sessionId)
                .remove();
    }

    /**
     * 上传单个用户附件并记录会话文件元数据。
     */
    private AgentConversationFile uploadFile(String userId, String sessionId, MultipartFile upload) {
        String fileName = safeFileName(upload.getOriginalFilename());
        long fileId = snowflake.nextId();
        String objectKey = objectKey(userId, sessionId, fileId, fileName);
        String contentType = detectContentType(fileName);
        try (InputStream input = upload.getInputStream()) {
            validateFileSize(upload.getSize());
            if (ImageUtil.contentType(fileName) == null) {
                putObject(objectKey, contentType, input, upload.getSize());
            } else {
                byte[] content = readImageBytes(input);
                contentType = requireImageType(content);
                putObject(objectKey, contentType, new ByteArrayInputStream(content), content.length);
            }
        } catch (XException error) {
            throw error;
        } catch (Exception error) {
            throw storageError("read upload", objectKey, error);
        }

        AgentConversationFile file = new AgentConversationFile()
                .setId(fileId)
                .setSessionId(sessionId)
                .setUserId(userId)
                .setKind(AgentConversationFileKind.INPUT)
                .setFileName(fileName)
                .setObjectKey(objectKey)
                .setSandboxPath(inputSandboxPath(sessionId, fileId, fileName))
                .setContentType(contentType)
                .setSizeBytes(upload.getSize());
        saveMetadataOrRemoveObject(file);
        return file;
    }

    /**
     * 校验并绑定本轮选择的附件。
     */
    private List<AgentConversationFile> bindSelectedFiles(String userId, String sessionId, List<Long> ids,
                                                          Long messageId) {
        List<AgentConversationFile> files = agentConversationFileIService.lambdaQuery()
                .eq(AgentConversationFile::getUserId, userId)
                .eq(AgentConversationFile::getSessionId, sessionId)
                .eq(AgentConversationFile::getKind, AgentConversationFileKind.INPUT)
                .in(AgentConversationFile::getId, ids)
                .orderByAsc(AgentConversationFile::getId)
                .list();
        if (files.size() != ids.size() || files.stream().anyMatch(file -> file.getMessageId() != null)) {
            throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_INVALID);
        }
        if (agentConversationFileIService.bindPendingInputs(userId, sessionId, ids, messageId) != ids.size()) {
            throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_INVALID);
        }
        files.forEach(file -> file.setMessageId(messageId));
        return List.copyOf(files);
    }

    /**
     * 从 S3 恢复文件，并写入当前 AgentScope 沙箱的会话目录。
     */
    private void materialize(AgentConversationFile file, AbstractFilesystem filesystem, RuntimeContext context) {
        if (filesystem.exists(context, file.getSandboxPath())) {
            return;
        }
        try (InputStream input = getObject(file.getObjectKey())) {
            validateFileSize(file.getSizeBytes());
            int expectedSize = Math.toIntExact(file.getSizeBytes());
            byte[] content = input.readNBytes(Math.addExact(expectedSize, 1));
            if (content.length != file.getSizeBytes()) {
                throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_STORAGE_ERROR);
            }
            FileUploadResponse uploaded = filesystem
                    .uploadFiles(context, List.of(Map.entry(file.getSandboxPath(), content)))
                    .getFirst();
            if (!uploaded.isSuccess()) {
                throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_STORAGE_ERROR);
            }
        } catch (XException error) {
            throw error;
        } catch (Exception error) {
            throw storageError("materialize", file.getObjectKey(), error);
        }
    }

    /**
     * 获取 AgentScope 为当前调用注入的文件系统，并校验必要身份。
     */
    private AbstractFilesystem requireFilesystem(RuntimeContext context) {
        if (context == null || StringUtil.isBlank(context.getUserId()) || StringUtil.isBlank(context.getSessionId())) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        AbstractFilesystem filesystem = context.get(AbstractFilesystem.class);
        if (filesystem == null) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        return filesystem;
    }

    /**
     * 限制生成文件大小，避免单个产物耗尽对象存储或服务内存。
     */
    private void validateFileSize(long sizeBytes) {
        if (sizeBytes < 0 || sizeBytes > agentProperties.getConversationFile().getMaxFileSizeBytes()
                || sizeBytes >= Integer.MAX_VALUE) {
            throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_INVALID);
        }
    }

    /**
     * 将工具传入的路径约束在当前会话输出目录。
     */
    private String validateOutputPath(String sessionId, String sandboxPath) {
        if (StringUtil.isBlank(sandboxPath)) {
            throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_INVALID);
        }
        String relative = sandboxPath.replace(StrPool.C_BACKSLASH, StrPool.C_SLASH);
        String workspaceRoot = agentProperties.getSandbox().getWorkspaceRoot();
        String workspacePrefix = workspaceRoot.endsWith(StrPool.SLASH)
                ? workspaceRoot
                : workspaceRoot + StrPool.SLASH;
        if (relative.startsWith(workspacePrefix)) {
            relative = relative.substring(workspacePrefix.length());
        }
        Path normalized = Path.of(relative).normalize();
        String normalizedPath = normalized.toString().replace(StrPool.C_BACKSLASH, StrPool.C_SLASH);
        String outputRoot = String.join(StrPool.SLASH, AgentConstant.CONVERSATION_FILE_WORKSPACE_DIRECTORY, sessionId,
                AgentConstant.CONVERSATION_FILE_OUTPUT_DIRECTORY) + StrPool.SLASH;
        if (normalized.isAbsolute() || !normalizedPath.startsWith(outputRoot)) {
            throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_INVALID);
        }
        return normalizedPath;
    }

    /**
     * 提取安全的基础文件名；文件内容和格式由 Agent 在沙箱中判断处理。
     */
    private String safeFileName(String originalFileName) {
        if (StringUtil.isBlank(originalFileName)) {
            throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_INVALID);
        }
        String normalized = originalFileName.replace(StrPool.C_BACKSLASH, StrPool.C_SLASH);
        String fileName = Path.of(normalized).getFileName().toString();
        if (StringUtil.isBlank(fileName)) {
            throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_INVALID);
        }
        return fileName;
    }

    /**
     * 使用会话和文件 ID 构建不会互相覆盖的沙箱输入路径。
     */
    private String inputSandboxPath(String sessionId, long fileId, String fileName) {
        return String.join(StrPool.SLASH, AgentConstant.CONVERSATION_FILE_WORKSPACE_DIRECTORY, sessionId,
                AgentConstant.CONVERSATION_FILE_INPUT_DIRECTORY,
                fileId + StrPool.DASHED + fileName);
    }

    /**
     * 使用受控前缀和内部 ID 构建 S3 对象 Key。
     */
    private String objectKey(String userId, String sessionId, long fileId, String fileName) {
        String prefix = minioProperties.getConversationFileObjectPrefix();
        return String.join(StrPool.SLASH, prefix, userId, sessionId, String.valueOf(fileId), fileName);
    }

    /**
     * 写入 S3 对象。
     */
    private void putObject(String objectKey, String contentType, InputStream input, long sizeBytes) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .stream(input, sizeBytes, -1L)
                    .contentType(contentType)
                    .build());
        } catch (Exception error) {
            throw storageError("upload", objectKey, error);
        }
    }

    /**
     * 读取 S3 对象。
     */
    private InputStream getObject(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception error) {
            throw storageError("read", objectKey, error);
        }
    }

    /**
     * 删除 S3 对象，删除操作保持幂等。
     */
    private void removeObject(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception error) {
            throw storageError("delete", objectKey, error);
        }
    }

    /**
     * 元数据保存失败时回收已经上传的 S3 对象。
     */
    private void saveMetadataOrRemoveObject(AgentConversationFile file) {
        try {
            if (!agentConversationFileIService.save(file)) {
                throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_STORAGE_ERROR);
            }
        } catch (RuntimeException error) {
            try {
                removeObject(file.getObjectKey());
            } catch (RuntimeException cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }
    }

    /**
     * 批量上传中途失败时回收本批次已经落库和写入 S3 的文件。
     */
    private void rollbackUploadedFiles(List<AgentConversationFile> uploaded, RuntimeException originalError) {
        for (AgentConversationFile file : uploaded) {
            try {
                removeObject(file.getObjectKey());
                agentConversationFileIService.removeById(file.getId());
            } catch (RuntimeException cleanupError) {
                originalError.addSuppressed(cleanupError);
            }
        }
    }

    /**
     * 根据文件元数据校验文件属于指定用户和会话。
     */
    private AgentConversationFile requireFile(String userId, String sessionId, Long fileId) {
        AgentConversationFile file = agentConversationFileIService.getById(fileId);
        if (file == null || !userId.equals(file.getUserId()) || !sessionId.equals(file.getSessionId())) {
            throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_NOT_FOUND);
        }
        return file;
    }

    /**
     * 根据扩展名返回常见文件媒体类型；SVG 可预览，但不作为原生模型图片输入。
     */
    private String detectContentType(String fileName) {
        String imageType = ImageUtil.contentType(fileName);
        if (imageType != null) {
            return imageType;
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lowerName.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF_VALUE;
        }
        if (lowerName.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lowerName.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (lowerName.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        if (lowerName.endsWith(".json")) {
            return MediaType.APPLICATION_JSON_VALUE;
        }
        if (lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".csv")
                || lowerName.endsWith(".tsv") || lowerName.endsWith(".yaml") || lowerName.endsWith(".yml")) {
            return MediaType.TEXT_PLAIN_VALUE;
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    /**
     * 重新校验文件归属后读取图片，模型不需要访问受鉴权保护的文件下载地址。
     */
    private ContentBlock readInputImage(String userId, String sessionId, Long fileId) {
        AgentConversationFile file = requireFile(userId, sessionId, fileId);
        if (file.getKind() != AgentConversationFileKind.INPUT || file.getMessageId() == null) {
            throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_INVALID);
        }
        try (InputStream input = getObject(file.getObjectKey())) {
            byte[] content = readImageBytes(input);
            return DataBlock.builder().name(file.getFileName()).source(Base64Source.builder()
                    .mediaType(requireImageType(content))
                    .data(Base64.getEncoder().encodeToString(content)).build()).build();
        } catch (XException error) {
            throw error;
        } catch (Exception error) {
            throw storageError("read image", file.getObjectKey(), error);
        }
    }

    /**
     * DataBlock 也可能包含音视频，只将显式声明为图片的来源交给图片发布流程。
     */
    private boolean isImageSource(URLSource source) {
        return source.getUrl().startsWith("data:image/")
                || source.getMimeType() != null && source.getMimeType().startsWith("image/");
    }

    /**
     * 读取模型图片；远端图片只访问配置的域名，不携带模型凭证或跟随重定向。
     */
    private byte[] readImageSource(Source source) {
        if (source instanceof Base64Source base64) {
            return decodeImage(base64.getData());
        }
        String url = ((URLSource) source).getUrl();
        if (url.startsWith("data:")) {
            int separator = url.indexOf(',');
            if (separator < 0 || !url.substring(0, separator).endsWith(";base64")) {
                throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_IMAGE_INVALID);
            }
            return decodeImage(url.substring(separator + 1));
        }
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getPort() != -1 && uri.getPort() != 443
                    || multimodalProperties.getImageHosts().stream().noneMatch(uri.getHost()::equalsIgnoreCase)) {
                throw new XException(ResponseCodeEnum.AGENT_MODEL_IMAGE_HOST_DENIED);
            }
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(30_000);
            try {
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new XException(ResponseCodeEnum.AGENT_MODEL_IMAGE_DOWNLOAD_ERROR);
                }
                try (InputStream input = connection.getInputStream()) {
                    return readImageBytes(input);
                }
            } finally {
                connection.disconnect();
            }
        } catch (XException error) {
            throw error;
        } catch (Exception error) {
            throw new XException(ResponseCodeEnum.AGENT_MODEL_IMAGE_DOWNLOAD_ERROR);
        }
    }

    /**
     * 在解码前限制长度，防止模型返回过大的 Base64 数据。
     */
    private byte[] decodeImage(String data) {
        if (data == null || data.length() > ((long) multimodalProperties.getMaxImageSizeBytes() + 2) / 3 * 4) {
            throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_IMAGE_INVALID);
        }
        try {
            byte[] content = Base64.getDecoder().decode(data);
            requireImageType(content);
            return content;
        } catch (IllegalArgumentException error) {
            throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_IMAGE_INVALID);
        }
    }

    /**
     * 限量读取图片，不依赖远端声明的 Content-Length。
     */
    private byte[] readImageBytes(InputStream input) throws java.io.IOException {
        byte[] content = input.readNBytes(Math.addExact(multimodalProperties.getMaxImageSizeBytes(), 1));
        requireImageType(content);
        return content;
    }

    /**
     * 验证图片大小和实际格式，拒绝伪装为图片的 HTML、SVG 及其他文件。
     */
    private String requireImageType(byte[] content) {
        String contentType = ImageUtil.contentType(content);
        if (content.length > multimodalProperties.getMaxImageSizeBytes() || contentType == null) {
            throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_IMAGE_INVALID);
        }
        return contentType;
    }

    /**
     * 将数据库文件实体转换为 API 文件模型。
     */
    private ConversationFileDo toFile(AgentConversationFile file) {
        return new ConversationFileDo()
                .setId(String.valueOf(file.getId()))
                .setSessionId(file.getSessionId())
                .setMessageId(file.getMessageId() == null ? null : String.valueOf(file.getMessageId()))
                .setKind(file.getKind())
                .setName(file.getFileName())
                .setContentType(file.getContentType())
                .setSizeBytes(file.getSizeBytes())
                .setWorkspacePath(file.getSandboxPath())
                .setCreatedAt(file.getCreateTime() == null ? null : file.getCreateTime().toString());
    }

    /**
     * 记录对象存储异常并转换为统一业务错误。
     */
    private XException storageError(String operation, String objectKey, Exception error) {
        log.error("Failed to {} conversation file object: {}", operation, objectKey, error);
        return new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_STORAGE_ERROR);
    }
}
