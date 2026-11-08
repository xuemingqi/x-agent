package com.x.ai.service.impl;

import cn.hutool.core.text.StrPool;
import com.x.ai.common.domain.KnowledgeDocumentDo;
import com.x.ai.common.domain.KnowledgeDownloadDo;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.properties.AgentProperties;
import com.x.ai.service.AdministratorAuthorizationService;
import com.x.ai.service.PublicKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicKnowledgeServiceImpl implements PublicKnowledgeService {

    private static final int MAX_TEXT_CONTENT_LENGTH = 100_000;

    private final AdministratorAuthorizationService authorizationService;

    private final AgentProperties agentProperties;


    @Override
    public List<KnowledgeDocumentDo> list(String actorUserId) {
        authorizationService.requireAdministrator(actorUserId);
        Path directory = publicDirectory();
        try (var paths = Files.walk(directory)) {
            return paths.filter(path -> Files.isRegularFile(path) && !Files.isSymbolicLink(path))
                    .map(path -> toDocument(directory, path))
                    .sorted(Comparator.comparing(KnowledgeDocumentDo::getPath))
                    .toList();
        } catch (IOException error) {
            throw storageError("list", directory, error);
        }
    }

    @Override
    public List<KnowledgeDocumentDo> upload(String actorUserId, List<MultipartFile> files) {
        authorizationService.requireAdministrator(actorUserId);
        if (files == null || files.isEmpty()) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        Path directory = publicDirectory();
        return files.stream().map(file -> upload(directory, file)).toList();
    }

    @Override
    public KnowledgeDocumentDo writeText(String actorUserId, String path, String content) {
        authorizationService.requireAdministrator(actorUserId);
        if (StringUtil.isBlank(content) || content.length() > MAX_TEXT_CONTENT_LENGTH) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        Path directory = publicDirectory();
        Path target = resolveDocument(directory, path);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return toDocument(directory, target);
        } catch (IOException error) {
            throw storageError("write", target, error);
        }
    }

    @Override
    public KnowledgeDownloadDo download(String actorUserId, String path) {
        authorizationService.requireAdministrator(actorUserId);
        Path file = requireDocument(path);
        try {
            return new KnowledgeDownloadDo()
                    .setFileName(file.getFileName().toString())
                    .setContentType(contentType(file))
                    .setResource(new UrlResource(file.toUri()));
        } catch (IOException error) {
            throw storageError("download", file, error);
        }
    }

    @Override
    public boolean delete(String actorUserId, String path) {
        authorizationService.requireAdministrator(actorUserId);
        Path file = requireDocument(path);
        try {
            return Files.deleteIfExists(file);
        } catch (IOException error) {
            throw storageError("delete", file, error);
        }
    }

    /**
     * 校验上传文件后写入公共知识库根目录。
     */
    private KnowledgeDocumentDo upload(Path directory, MultipartFile file) {
        String fileName = safeFileName(file == null ? null : file.getOriginalFilename());
        if (file.isEmpty() || file.getSize() > agentProperties.getUserWorkspace().getMaxUploadSizeBytes()) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        Path target = resolveDocument(directory, fileName);
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            return toDocument(directory, target);
        } catch (IOException error) {
            throw storageError("upload", target, error);
        }
    }

    /**
     * 去除浏览器本地目录，只接受普通文件名。
     */
    private String safeFileName(String originalFileName) {
        if (StringUtil.isBlank(originalFileName)) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        String normalized = originalFileName.replace(StrPool.C_BACKSLASH, StrPool.C_SLASH);
        String fileName = Path.of(normalized).getFileName().toString();
        if (fileName.isBlank() || StrPool.DOT.equals(fileName) || StrPool.DOUBLE_DOT.equals(fileName)) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        return fileName;
    }

    /**
     * 解析相对路径并阻止越过公共知识库根目录或写入符号链接。
     */
    private Path resolveDocument(Path directory, String path) {
        if (StringUtil.isBlank(path)) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        Path resolved = directory.resolve(path).normalize();
        if (!resolved.startsWith(directory) || resolved.equals(directory) || containsSymbolicLink(directory, resolved)) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        return resolved;
    }

    /**
     * 拒绝利用目录中的符号链接访问共享目录之外的文件。
     */
    private boolean containsSymbolicLink(Path directory, Path resolved) {
        Path current = directory;
        for (Path segment : directory.relativize(resolved)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取已存在的普通公共知识文件。
     */
    private Path requireDocument(String path) {
        Path file = resolveDocument(publicDirectory(), path);
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_NOT_FOUND);
        }
        return file;
    }

    /**
     * 延迟创建公共知识库宿主机目录。
     */
    private Path publicDirectory() {
        Path directory = agentProperties.getPublicKnowledge().getDirectory().toAbsolutePath().normalize();
        try {
            return Files.createDirectories(directory);
        } catch (IOException error) {
            throw storageError("initialize", directory, error);
        }
    }

    /**
     * 读取文件元数据并转换为页面领域对象。
     */
    private KnowledgeDocumentDo toDocument(Path directory, Path file) {
        try {
            return new KnowledgeDocumentDo()
                    .setPath(directory.relativize(file).toString().replace(StrPool.C_BACKSLASH, StrPool.C_SLASH))
                    .setName(file.getFileName().toString())
                    .setSize(Files.size(file))
                    .setContentType(contentType(file))
                    .setUpdateTime(LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(),
                            ZoneId.systemDefault()));
        } catch (IOException error) {
            throw storageError("read metadata", file, error);
        }
    }

    /**
     * 识别文档类型，无法识别时返回通用二进制类型。
     */
    private String contentType(Path file) throws IOException {
        String contentType = Files.probeContentType(file);
        return contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType;
    }

    /**
     * 记录公共知识库文件系统异常并转换为统一错误。
     */
    private XException storageError(String operation, Path path, IOException error) {
        log.error("Failed to {} public knowledge document: {}", operation, path, error);
        return new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_STORAGE_ERROR);
    }
}
