package com.x.ai.service.impl;

import com.x.ai.common.domain.KnowledgeBaseDo;
import com.x.ai.common.domain.KnowledgeChunkPageDo;
import com.x.ai.common.domain.KnowledgeDocumentDo;
import com.x.ai.common.domain.KnowledgeDownloadDo;
import com.x.ai.common.domain.KnowledgePreviewDo;
import com.x.ai.common.domain.KnowledgeSearchResultDo;
import com.x.ai.service.KnowledgeBaseService;
import com.x.ai.service.KnowledgeService;
import com.x.ai.service.RagKnowledgeService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.UserThreadLocalUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final RagKnowledgeService ragKnowledgeService;

    private final KnowledgeBaseService knowledgeBaseService;

    @Override
    public List<KnowledgeDocumentDo> list(String knowledgeBaseId) {
        KnowledgeBaseDo base = requireBase(knowledgeBaseId);
        return ragKnowledgeService.list(UserThreadLocalUtil.getUserId(), knowledgeBaseId).stream()
                .map(document -> document.setKnowledgeBaseName(base.getName())).toList();
    }

    @Override
    public List<KnowledgeDocumentDo> upload(String knowledgeBaseId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        KnowledgeBaseDo base = requireBase(knowledgeBaseId);
        String userId = UserThreadLocalUtil.getUserId();
        return files.stream().map(file -> ragKnowledgeService.upload(userId, knowledgeBaseId, file)
                .setKnowledgeBaseName(base.getName())).toList();
    }

    @Override
    public KnowledgeDownloadDo download(String knowledgeBaseId, String path) {
        requireBase(knowledgeBaseId);
        return ragKnowledgeService.download(UserThreadLocalUtil.getUserId(), knowledgeBaseId, path);
    }

    @Override
    public boolean delete(String knowledgeBaseId, String path) {
        requireBase(knowledgeBaseId);
        return ragKnowledgeService.delete(UserThreadLocalUtil.getUserId(), knowledgeBaseId, path);
    }

    @Override
    public KnowledgePreviewDo preview(String knowledgeBaseId, String path) {
        requireBase(knowledgeBaseId);
        return ragKnowledgeService.preview(UserThreadLocalUtil.getUserId(), knowledgeBaseId, path);
    }

    @Override
    public KnowledgeChunkPageDo chunks(String knowledgeBaseId, String path, int page, int size) {
        requireBase(knowledgeBaseId);
        return ragKnowledgeService.chunks(UserThreadLocalUtil.getUserId(), knowledgeBaseId, path, page, size);
    }

    @Override
    public KnowledgeDocumentDo reindex(String knowledgeBaseId, String path) {
        KnowledgeBaseDo base = requireBase(knowledgeBaseId);
        return ragKnowledgeService.reindex(UserThreadLocalUtil.getUserId(), knowledgeBaseId, path)
                .setKnowledgeBaseName(base.getName());
    }

    @Override
    public List<KnowledgeSearchResultDo> search(String knowledgeBaseId, String query, int limit) {
        requireBase(knowledgeBaseId);
        return ragKnowledgeService.search(UserThreadLocalUtil.getUserId(), List.of(knowledgeBaseId), query, limit);
    }

    /**
     * 文档管理限定一个明确且属于当前用户的知识库。
     */
    private KnowledgeBaseDo requireBase(String knowledgeBaseId) {
        return knowledgeBaseService.requireOwned(UserThreadLocalUtil.getUserId(), List.of(knowledgeBaseId)).getFirst();
    }
}
