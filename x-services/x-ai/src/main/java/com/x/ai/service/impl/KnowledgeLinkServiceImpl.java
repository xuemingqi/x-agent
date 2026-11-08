package com.x.ai.service.impl;

import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.ai.common.domain.KnowledgeLinkDo;
import com.x.ai.common.dto.KnowledgeLinkCreateDto;
import com.x.ai.common.dto.KnowledgeLinkUpdateDto;
import com.x.ai.common.model.KnowledgeLinkParam;
import com.x.ai.service.KnowledgeLinkManagementService;
import com.x.ai.service.KnowledgeLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeLinkServiceImpl implements KnowledgeLinkService {

    private final KnowledgeLinkManagementService knowledgeLinkManagementService;


    @Override
    public List<KnowledgeLinkDo> list() {
        return knowledgeLinkManagementService.list(currentUserId());
    }

    @Override
    public KnowledgeLinkDo create(KnowledgeLinkCreateDto request) {
        return knowledgeLinkManagementService.create(currentUserId(), toParam(request.getName(), request.getSummary(),
                request.getUrl(), request.getFolderId()));
    }

    @Override
    public KnowledgeLinkDo update(Long id, KnowledgeLinkUpdateDto request) {
        return knowledgeLinkManagementService.update(currentUserId(), id, toParam(request.getName(),
                request.getSummary(), request.getUrl(), request.getFolderId()));
    }

    @Override
    public boolean delete(Long id) {
        return knowledgeLinkManagementService.delete(currentUserId(), id);
    }

    /**
     * 将 Web 请求转换为不依赖传输层的内部参数。
     */
    private KnowledgeLinkParam toParam(String name, String summary, String url, Long folderId) {
        return new KnowledgeLinkParam()
                .setName(name)
                .setSummary(summary)
                .setUrl(url)
                .setFolderId(folderId);
    }

    /**
     * 从可信请求上下文读取当前登录用户 ID。
     */
    private String currentUserId() {
        return UserThreadLocalUtil.getUserId();
    }
}
