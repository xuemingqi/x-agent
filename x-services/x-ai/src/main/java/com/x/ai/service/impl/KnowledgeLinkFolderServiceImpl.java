package com.x.ai.service.impl;

import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.ai.common.domain.KnowledgeLinkFolderDo;
import com.x.ai.common.dto.KnowledgeLinkFolderCreateDto;
import com.x.ai.common.dto.KnowledgeLinkFolderUpdateDto;
import com.x.ai.service.KnowledgeLinkFolderManagementService;
import com.x.ai.service.KnowledgeLinkFolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeLinkFolderServiceImpl implements KnowledgeLinkFolderService {

    private final KnowledgeLinkFolderManagementService knowledgeLinkFolderManagementService;


    @Override
    public List<KnowledgeLinkFolderDo> list() {
        return knowledgeLinkFolderManagementService.list(currentUserId());
    }

    @Override
    public KnowledgeLinkFolderDo create(KnowledgeLinkFolderCreateDto request) {
        return knowledgeLinkFolderManagementService.create(currentUserId(), request.getParentId(), request.getName());
    }

    @Override
    public KnowledgeLinkFolderDo rename(Long id, KnowledgeLinkFolderUpdateDto request) {
        return knowledgeLinkFolderManagementService.rename(currentUserId(), id, request.getName());
    }

    @Override
    public boolean delete(Long id) {
        return knowledgeLinkFolderManagementService.delete(currentUserId(), id);
    }

    /**
     * 从可信请求上下文读取当前登录用户 ID。
     */
    private String currentUserId() {
        return UserThreadLocalUtil.getUserId();
    }
}
