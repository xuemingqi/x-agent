package com.x.ai.service;

import com.x.ai.common.domain.KnowledgeLinkDo;
import com.x.ai.common.domain.KnowledgeLinkFolderDo;
import com.x.ai.common.model.KnowledgeLinkParam;

import java.util.List;

public interface PublicKnowledgeLinkService {

    List<KnowledgeLinkDo> listLinks(String actorUserId);

    KnowledgeLinkDo createLink(String actorUserId, KnowledgeLinkParam param);

    KnowledgeLinkDo updateLink(String actorUserId, Long id, KnowledgeLinkParam param);

    boolean deleteLink(String actorUserId, Long id);

    List<KnowledgeLinkFolderDo> listFolders(String actorUserId);

    KnowledgeLinkFolderDo createFolder(String actorUserId, Long parentId, String name);

    KnowledgeLinkFolderDo renameFolder(String actorUserId, Long id, String name);

    boolean deleteFolder(String actorUserId, Long id);
}
