package com.x.ai.service.impl;

import com.x.ai.common.domain.KnowledgeLinkDo;
import com.x.ai.common.domain.KnowledgeLinkFolderDo;
import com.x.ai.common.model.KnowledgeLinkParam;
import com.x.ai.service.AdministratorAuthorizationService;
import com.x.ai.service.KnowledgeLinkFolderManagementService;
import com.x.ai.service.KnowledgeLinkManagementService;
import com.x.ai.service.PublicKnowledgeLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicKnowledgeLinkServiceImpl implements PublicKnowledgeLinkService {

    private final AdministratorAuthorizationService authorizationService;

    private final KnowledgeLinkManagementService linkManagementService;

    private final KnowledgeLinkFolderManagementService folderManagementService;


    @Override
    public List<KnowledgeLinkDo> listLinks(String actorUserId) {
        authorizationService.requireAdministrator(actorUserId);
        return linkManagementService.listPublic();
    }

    @Override
    public KnowledgeLinkDo createLink(String actorUserId, KnowledgeLinkParam param) {
        authorizationService.requireAdministrator(actorUserId);
        return linkManagementService.createPublic(actorUserId, param);
    }

    @Override
    public KnowledgeLinkDo updateLink(String actorUserId, Long id, KnowledgeLinkParam param) {
        authorizationService.requireAdministrator(actorUserId);
        return linkManagementService.updatePublic(id, param);
    }

    @Override
    public boolean deleteLink(String actorUserId, Long id) {
        authorizationService.requireAdministrator(actorUserId);
        return linkManagementService.deletePublic(id);
    }

    @Override
    public List<KnowledgeLinkFolderDo> listFolders(String actorUserId) {
        authorizationService.requireAdministrator(actorUserId);
        return folderManagementService.listPublic();
    }

    @Override
    public KnowledgeLinkFolderDo createFolder(String actorUserId, Long parentId, String name) {
        authorizationService.requireAdministrator(actorUserId);
        return folderManagementService.createPublic(actorUserId, parentId, name);
    }

    @Override
    public KnowledgeLinkFolderDo renameFolder(String actorUserId, Long id, String name) {
        authorizationService.requireAdministrator(actorUserId);
        return folderManagementService.renamePublic(id, name);
    }

    @Override
    public boolean deleteFolder(String actorUserId, Long id) {
        authorizationService.requireAdministrator(actorUserId);
        return folderManagementService.deletePublic(id);
    }
}
