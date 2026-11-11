package com.x.ai.service.impl;

import com.x.ai.common.constants.KnowledgeConstant;
import com.x.ai.common.domain.KnowledgeChunkPageDo;
import com.x.ai.common.domain.KnowledgeDocumentDo;
import com.x.ai.common.domain.KnowledgeDownloadDo;
import com.x.ai.common.domain.KnowledgePreviewDo;
import com.x.ai.common.domain.KnowledgeSearchResultDo;
import com.x.ai.service.AdministratorAuthorizationService;
import com.x.ai.service.PublicKnowledgeService;
import com.x.ai.service.RagKnowledgeService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicKnowledgeServiceImpl implements PublicKnowledgeService {

    private static final int MAX_TEXT_CONTENT_LENGTH = 100_000;

    private final AdministratorAuthorizationService authorizationService;

    private final RagKnowledgeService ragKnowledgeService;

    @Override
    public List<KnowledgeDocumentDo> list(String actorUserId) {
        authorizationService.requireAdministrator(actorUserId);
        return ragKnowledgeService.list(KnowledgeConstant.PUBLIC_OWNER);
    }

    @Override
    public List<KnowledgeDocumentDo> upload(String actorUserId, List<MultipartFile> files) {
        authorizationService.requireAdministrator(actorUserId);
        if (files == null || files.isEmpty()) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        return files.stream().map(file -> ragKnowledgeService.upload(KnowledgeConstant.PUBLIC_OWNER, file)).toList();
    }

    @Override
    public KnowledgeDocumentDo writeText(String actorUserId, String path, String content) {
        authorizationService.requireAdministrator(actorUserId);
        if (content == null || content.isBlank() || content.length() > MAX_TEXT_CONTENT_LENGTH) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        return ragKnowledgeService.upload(KnowledgeConstant.PUBLIC_OWNER, path, content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public KnowledgeDownloadDo download(String actorUserId, String path) {
        authorizationService.requireAdministrator(actorUserId);
        return ragKnowledgeService.download(KnowledgeConstant.PUBLIC_OWNER, path);
    }

    @Override
    public boolean delete(String actorUserId, String path) {
        authorizationService.requireAdministrator(actorUserId);
        return ragKnowledgeService.delete(KnowledgeConstant.PUBLIC_OWNER, path);
    }

    @Override
    public KnowledgePreviewDo preview(String actorUserId, String path) {
        authorizationService.requireAdministrator(actorUserId);
        return ragKnowledgeService.preview(KnowledgeConstant.PUBLIC_OWNER, path);
    }

    @Override
    public KnowledgeChunkPageDo chunks(String actorUserId, String path, int page, int size) {
        authorizationService.requireAdministrator(actorUserId);
        return ragKnowledgeService.chunks(KnowledgeConstant.PUBLIC_OWNER, path, page, size);
    }

    @Override
    public KnowledgeDocumentDo reindex(String actorUserId, String path) {
        authorizationService.requireAdministrator(actorUserId);
        return ragKnowledgeService.reindex(KnowledgeConstant.PUBLIC_OWNER, path);
    }

    @Override
    public List<KnowledgeSearchResultDo> search(String actorUserId, String query, int limit) {
        authorizationService.requireAdministrator(actorUserId);
        return ragKnowledgeService.searchPublic(query, limit);
    }

}
