package com.x.ai.service.impl;

import com.x.ai.common.domain.KnowledgeChunkPageDo;
import com.x.ai.common.domain.KnowledgeDocumentDo;
import com.x.ai.common.domain.KnowledgeDownloadDo;
import com.x.ai.common.domain.KnowledgePreviewDo;
import com.x.ai.common.domain.KnowledgeSearchResultDo;
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

    @Override
    public List<KnowledgeDocumentDo> list() {
        return ragKnowledgeService.list(UserThreadLocalUtil.getUserId());
    }

    @Override
    public List<KnowledgeDocumentDo> upload(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INVALID);
        }
        String userId = UserThreadLocalUtil.getUserId();
        return files.stream().map(file -> ragKnowledgeService.upload(userId, file)).toList();
    }

    @Override
    public KnowledgeDownloadDo download(String path) {
        return ragKnowledgeService.download(UserThreadLocalUtil.getUserId(), path);
    }

    @Override
    public boolean delete(String path) {
        return ragKnowledgeService.delete(UserThreadLocalUtil.getUserId(), path);
    }

    @Override
    public KnowledgePreviewDo preview(String path) {
        return ragKnowledgeService.preview(UserThreadLocalUtil.getUserId(), path);
    }

    @Override
    public KnowledgeChunkPageDo chunks(String path, int page, int size) {
        return ragKnowledgeService.chunks(UserThreadLocalUtil.getUserId(), path, page, size);
    }

    @Override
    public KnowledgeDocumentDo reindex(String path) {
        return ragKnowledgeService.reindex(UserThreadLocalUtil.getUserId(), path);
    }

    @Override
    public List<KnowledgeSearchResultDo> search(String query, int limit) {
        return ragKnowledgeService.search(UserThreadLocalUtil.getUserId(), query, limit);
    }

}
