package com.x.ai.service.impl;

import com.x.ai.common.domain.KnowledgeChunkPageDo;
import com.x.ai.common.domain.KnowledgeDocumentDo;
import com.x.ai.common.domain.KnowledgeDownloadDo;
import com.x.ai.common.domain.KnowledgePreviewDo;
import com.x.ai.common.domain.KnowledgeSearchResultDo;
import com.x.ai.common.util.DocumentTextUtil;
import com.x.ai.common.util.TextChunkUtil;
import com.x.ai.db.entity.KnowledgeDocument;
import com.x.ai.db.service.KnowledgeDocumentIService;
import com.x.ai.properties.KnowledgeEmbeddingProperties;
import com.x.ai.properties.KnowledgeProperties;
import com.x.ai.service.KnowledgeEmbeddingService;
import com.x.ai.service.RagKnowledgeService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagKnowledgeServiceImpl implements RagKnowledgeService {

    private static final int MAX_QUERY_CHARACTERS = 2000;

    private static final int MAX_PATH_CHARACTERS = 1024;

    private static final int MAX_PATH_BYTES = 2000;

    private static final int MAX_NAME_CHARACTERS = 255;

    private static final int MAX_CHUNK_PAGE_SIZE = 100;

    private static final int MAX_SEARCH_RESULTS = 20;

    private final KnowledgeDocumentIService documents;

    private final KnowledgeEmbeddingService embeddingService;

    private final KnowledgeProperties properties;

    private final KnowledgeEmbeddingProperties embeddingProperties;

    @Override
    public List<KnowledgeDocumentDo> list(String ownerId, String knowledgeBaseId) {
        return storage(() -> documents.list(ownerId, knowledgeBaseId).stream().map(this::toDocument).toList());
    }

    @Override
    public KnowledgeDocumentDo upload(String ownerId, String knowledgeBaseId, MultipartFile file) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null
                || file.getSize() > properties.getMaxUploadSizeBytes()) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        String originalName = file.getOriginalFilename().replace('\\', '/');
        String fileName = originalName.substring(originalName.lastIndexOf('/') + 1);
        try {
            return upload(ownerId, knowledgeBaseId, fileName, file.getBytes());
        } catch (IOException error) {
            log.warn("Failed to read uploaded knowledge document", error);
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_STORAGE_ERROR);
        }
    }

    @Override
    public KnowledgeDocumentDo upload(String ownerId, String knowledgeBaseId, String path, byte[] source) {
        String normalized = validPath(path);
        if (source == null || source.length == 0
                || source.length > properties.getMaxUploadSizeBytes()) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        String name = Path.of(normalized).getFileName().toString();
        String contentType;
        try {
            contentType = DocumentTextUtil.contentType(name);
        } catch (IllegalArgumentException error) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        return storage(() -> index(documents.stage(ownerId, knowledgeBaseId, normalized, name, contentType, source)));
    }

    @Override
    public KnowledgeDownloadDo download(String ownerId, String knowledgeBaseId, String path) {
        return storage(() -> {
            KnowledgeDocument document = requireDocument(ownerId, knowledgeBaseId, validPath(path), true);
            return new KnowledgeDownloadDo().setFileName(document.getName()).setContentType(document.getContentType())
                    .setResource(new ByteArrayResource(document.getSource()));
        });
    }

    @Override
    public boolean delete(String ownerId, String knowledgeBaseId, String path) {
        return storage(() -> {
            if (!documents.delete(ownerId, knowledgeBaseId, validPath(path))) {
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_NOT_FOUND);
            }
            return true;
        });
    }

    @Override
    public KnowledgePreviewDo preview(String ownerId, String knowledgeBaseId, String path) {
        return storage(() -> {
            KnowledgeDocument document = requireDocument(ownerId, knowledgeBaseId, validPath(path), false);
            return new KnowledgePreviewDo().setPath(document.getPath()).setName(document.getName())
                    .setContent(document.getContent()).setContentType(document.getContentType());
        });
    }

    @Override
    public KnowledgeChunkPageDo chunks(String ownerId, String knowledgeBaseId, String path, int page, int size) {
        if (page < 1 || size < 1 || size > MAX_CHUNK_PAGE_SIZE) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        return storage(() -> {
            String normalized = validPath(path);
            requireDocument(ownerId, knowledgeBaseId, normalized, false);
            return documents.chunks(ownerId, knowledgeBaseId, normalized, page, size);
        });
    }

    @Override
    public KnowledgeDocumentDo reindex(String ownerId, String knowledgeBaseId, String path) {
        return storage(() -> {
            KnowledgeDocument document = documents.restart(ownerId, knowledgeBaseId, validPath(path));
            if (document == null) throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_NOT_FOUND);
            return index(document);
        });
    }

    @Override
    public List<KnowledgeSearchResultDo> search(String userId, List<String> knowledgeBaseIds, String query, int limit) {
        if (query == null || query.isBlank() || query.length() > MAX_QUERY_CHARACTERS
                || limit < 1 || limit > MAX_SEARCH_RESULTS) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_SEARCH_INVALID);
        }
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) return List.of();
        boolean hasChunks = storage(() -> documents.hasSearchableChunks(userId, knowledgeBaseIds,
                embeddingProperties.getModel(), embeddingProperties.getDimensions()));
        if (!hasChunks) return List.of();
        List<Double> embedding = embeddingService.embed(List.of(query.strip())).getFirst();
        return storage(() -> documents.search(userId, knowledgeBaseIds, embedding, embeddingProperties.getModel(),
                embeddingProperties.getDimensions(), properties.getMinScore(), limit));
    }

    /**
     * 将新版本解析、保存正文和向量化，只有完整发布后才可参与检索。
     */
    private KnowledgeDocumentDo index(KnowledgeDocument document) {
        String content;
        try {
            content = DocumentTextUtil.extract(document.getName(), document.getSource(),
                    properties.getMaxContentCharacters());
        } catch (IOException error) {
            log.warn("Knowledge document parsing failed, owner={}, path={}", document.getOwnerId(), document.getPath());
            documents.fail(document, "文档无法提取文本，请确认格式有效、文本使用 UTF-8，PDF 非扫描件，且未超过正文上限。");
            return currentDocument(document);
        }
        if (!documents.saveContent(document, content)) return currentDocument(document);
        List<String> chunks = TextChunkUtil.split(content, properties.getChunkSize(), properties.getChunkOverlap());
        List<List<Double>> embeddings;
        try {
            embeddings = embeddingService.embed(chunks);
        } catch (XException error) {
            documents.fail(document, "向量化失败，请检查 Ollama 服务、模型名称和向量维度配置后重新索引。");
            return currentDocument(document);
        }
        documents.publish(document, content, chunks, embeddings,
                embeddingProperties.getModel(), embeddingProperties.getDimensions());
        return currentDocument(document);
    }

    /**
     * 索引并发被替换时返回最新状态，被删除时保持不存在，禁止复活旧版本。
     */
    private KnowledgeDocumentDo currentDocument(KnowledgeDocument document) {
        return toDocument(requireDocument(document.getOwnerId(), document.getKnowledgeBaseId(), document.getPath(), false));
    }

    /**
     * 按所有者查找，未找到统一返回不存在，避免泄露其他用户文档。
     */
    private KnowledgeDocument requireDocument(String ownerId, String knowledgeBaseId, String path, boolean includeSource) {
        KnowledgeDocument document = documents.find(ownerId, knowledgeBaseId, path, includeSource);
        if (document == null) throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_NOT_FOUND);
        return document;
    }

    /**
     * 规范化文档相对路径，拒绝绝对路径、回退和空片段。
     */
    private String validPath(String path) {
        if (path == null || path.isBlank() || path.length() > MAX_PATH_CHARACTERS
                || path.getBytes(StandardCharsets.UTF_8).length > MAX_PATH_BYTES || path.indexOf('\\') >= 0) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        try {
            Path parsed = Path.of(path);
            if (parsed.isAbsolute() || parsed.getFileName().toString().length() > MAX_NAME_CHARACTERS
                    || path.contains(":") || path.startsWith("/") || path.endsWith("/")) {
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
            }
            for (String segment : path.split("/", -1)) {
                if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                    throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
                }
            }
            return path;
        } catch (InvalidPathException error) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
    }

    /**
     * 持久化快照转换为不含正文与凭据的页面领域对象。
     */
    private KnowledgeDocumentDo toDocument(KnowledgeDocument document) {
        return new KnowledgeDocumentDo().setKnowledgeBaseId(document.getKnowledgeBaseId())
                .setPath(document.getPath()).setName(document.getName())
                .setSize(document.getSize()).setContentType(document.getContentType())
                .setUpdateTime(document.getUpdateTime()).setStatus(document.getStatus() == null ? null : document.getStatus().getCode())
                .setChunkCount(document.getChunkCount()).setEmbeddingModel(document.getEmbeddingModel())
                .setErrorMessage(document.getErrorMessage());
    }

    /**
     * 统一映射独立向量库访问错误，完整堆栈仅记录在服务日志中。
     */
    private <T> T storage(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException error) {
            log.error("Knowledge vector database operation failed", error);
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_STORAGE_ERROR);
        }
    }
}
