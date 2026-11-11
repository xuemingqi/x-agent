package com.x.ai.service.impl;

import com.x.ai.common.domain.KnowledgeLinkCatalogDo;
import com.x.ai.common.domain.KnowledgeLinkDetailDo;
import com.x.ai.common.model.ToolContext;
import com.x.ai.service.KnowledgeLinkManagementService;
import com.x.ai.service.KnowledgeLinkToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeLinkToolServiceImpl implements KnowledgeLinkToolService {

    private final KnowledgeLinkManagementService knowledgeLinkManagementService;


    @Override
    public List<KnowledgeLinkCatalogDo> catalog(ToolContext toolContext) {
        return knowledgeLinkManagementService.catalog(toolContext.getUserId());
    }

    @Override
    public KnowledgeLinkDetailDo get(ToolContext toolContext, Long id) {
        return knowledgeLinkManagementService.get(toolContext.getUserId(), id);
    }
}
