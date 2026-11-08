package com.x.ai.service.impl;

import com.x.framework.common.utils.UserThreadLocalUtil;

import cn.hutool.core.text.StrPool;
import com.x.ai.common.domain.KnowledgeDocumentDo;
import com.x.ai.common.domain.KnowledgeDownloadDo;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.properties.AgentProperties;
import com.x.ai.service.KnowledgeService;
import com.x.ai.service.HarnessAgentService;
import com.x.ai.service.UserWorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
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
public class KnowledgeServiceImpl implements KnowledgeService {


    private final UserWorkspaceService userWorkspaceService;

    private final AgentProperties agentProperties;

    private final HarnessAgentService harnessAgentService;

    @Override
    public List<KnowledgeDocumentDo> list() {
        Path directory = currentKnowledgeDirectory();
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
    public List<KnowledgeDocumentDo> upload(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        Path directory = currentKnowledgeDirectory();
        List<KnowledgeDocumentDo> documents = files.stream().map(file -> upload(directory, file)).toList();
        harnessAgentService.evictUserAgents(UserThreadLocalUtil.getUserId());
        return documents;
    }

    @Override
    public KnowledgeDownloadDo download(String path) {
        Path file = resolveDocument(currentKnowledgeDirectory(), path);
        try {
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_NOT_FOUND);
            }
            return new KnowledgeDownloadDo()
                    .setFileName(file.getFileName().toString())
                    .setContentType(contentType(file))
                    .setResource(new UrlResource(file.toUri()));
        } catch (IOException error) {
            throw storageError("download", file, error);
        }
    }

    @Override
    public boolean delete(String path) {
        Path file = resolveDocument(currentKnowledgeDirectory(), path);
        try {
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_NOT_FOUND);
            }
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                harnessAgentService.evictUserAgents(UserThreadLocalUtil.getUserId());
            }
            return deleted;
        } catch (IOException error) {
            throw storageError("delete", file, error);
        }
    }

    /**
     * 校验上传文件后写入用户知识库根目录。
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
     * 去除浏览器可能携带的本地目录，仅保留文件名。
     */
    private String safeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
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
     * 将用户输入的相对路径约束在当前用户知识库目录内。
     */
    private Path resolveDocument(Path directory, String path) {
        if (StringUtil.isEmpty(path)) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        Path resolved = directory.resolve(path).normalize();
        if (!resolved.startsWith(directory) || resolved.equals(directory) || containsSymbolicLink(directory, resolved)) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        return resolved;
    }

    /**
     * 知识目录可由用户沙箱写入，拒绝通过父目录符号链接访问其他用户或宿主机文件。
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
     * 读取文件元数据并转换为管理界面使用的文档对象。
     */
    private KnowledgeDocumentDo toDocument(Path directory, Path file) {
        try {
            return new KnowledgeDocumentDo()
                    .setPath(directory.relativize(file).toString()
                            .replace(StrPool.C_BACKSLASH, StrPool.C_SLASH))
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
     * 获取当前可信用户对应的知识库目录。
     */
    private Path currentKnowledgeDirectory() {
        String userId = UserThreadLocalUtil.getUserId();
        return userWorkspaceService.knowledgeDirectory(userId);
    }

    /**
     * 记录文件系统异常并转换为统一业务错误。
     */
    private XException storageError(String operation, Path path, IOException error) {
        log.error("Failed to {} knowledge document: {}", operation, path, error);
        return new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_STORAGE_ERROR);
    }
}
