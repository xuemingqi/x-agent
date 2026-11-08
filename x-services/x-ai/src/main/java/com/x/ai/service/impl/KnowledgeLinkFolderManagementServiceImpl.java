package com.x.ai.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.x.ai.common.constants.KnowledgeLinkConstant;
import com.x.ai.common.domain.KnowledgeLinkFolderDo;
import com.x.ai.db.entity.AgentKnowledgeLink;
import com.x.ai.db.entity.AgentKnowledgeLinkFolder;
import com.x.ai.db.enums.AgentKnowledgeLinkScope;
import com.x.ai.db.service.AgentKnowledgeLinkFolderIService;
import com.x.ai.db.service.AgentKnowledgeLinkIService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.service.KnowledgeLinkFolderManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KnowledgeLinkFolderManagementServiceImpl implements KnowledgeLinkFolderManagementService {

    private final AgentKnowledgeLinkFolderIService agentKnowledgeLinkFolderIService;

    private final AgentKnowledgeLinkIService agentKnowledgeLinkIService;

    private final Snowflake snowflake;


    @Override
    public List<KnowledgeLinkFolderDo> list(String userId) {
        return agentKnowledgeLinkFolderIService.lambdaQuery()
                .eq(AgentKnowledgeLinkFolder::getScope, AgentKnowledgeLinkScope.USER)
                .eq(AgentKnowledgeLinkFolder::getOwnerUserId, userId)
                .orderByAsc(AgentKnowledgeLinkFolder::getName)
                .list().stream()
                .map(folder -> toKnowledgeLinkFolderDo(folder, userId))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeLinkFolderDo create(String userId, Long parentId, String name) {
        String normalizedName = normalizeName(name);
        long targetParentId = parentId == null ? KnowledgeLinkConstant.ROOT_FOLDER_ID : parentId;
        lockOwned(userId, targetParentId);
        AgentKnowledgeLinkFolder folder = new AgentKnowledgeLinkFolder()
                .setId(snowflake.nextId())
                .setParentId(targetParentId)
                .setName(normalizedName)
                .setScope(AgentKnowledgeLinkScope.USER)
                .setOwnerUserId(userId)
                .setCreatedBy(userId);
        try {
            if (!agentKnowledgeLinkFolderIService.save(folder)) {
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_INVALID);
            }
        } catch (DuplicateKeyException error) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_CONFLICT);
        }
        return toKnowledgeLinkFolderDo(folder, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeLinkFolderDo rename(String userId, Long id, String name) {
        String normalizedName = normalizeName(name);
        AgentKnowledgeLinkFolder folder = ownedFolderForUpdate(userId, id);
        try {
            boolean updated = agentKnowledgeLinkFolderIService.lambdaUpdate()
                    .eq(AgentKnowledgeLinkFolder::getId, id)
                    .eq(AgentKnowledgeLinkFolder::getScope, AgentKnowledgeLinkScope.USER)
                    .eq(AgentKnowledgeLinkFolder::getOwnerUserId, userId)
                    .set(AgentKnowledgeLinkFolder::getName, normalizedName)
                    .update();
            if (!updated) {
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_NOT_FOUND);
            }
        } catch (DuplicateKeyException error) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_CONFLICT);
        }
        return toKnowledgeLinkFolderDo(folder.setName(normalizedName), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeLinkFolderDo move(String userId, Long id, Long parentId) {
        if (parentId == null || parentId < KnowledgeLinkConstant.ROOT_FOLDER_ID) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_INVALID);
        }
        AgentKnowledgeLinkFolder folder = ownedFolderForUpdate(userId, id);
        lockTargetAncestors(userId, id, parentId);
        if (parentId.equals(folder.getParentId())) {
            return toKnowledgeLinkFolderDo(folder, userId);
        }
        try {
            boolean updated = agentKnowledgeLinkFolderIService.lambdaUpdate()
                    .eq(AgentKnowledgeLinkFolder::getId, id)
                    .eq(AgentKnowledgeLinkFolder::getScope, AgentKnowledgeLinkScope.USER)
                    .eq(AgentKnowledgeLinkFolder::getOwnerUserId, userId)
                    .set(AgentKnowledgeLinkFolder::getParentId, parentId)
                    .update();
            if (!updated) {
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_NOT_FOUND);
            }
        } catch (DuplicateKeyException error) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_CONFLICT);
        }
        return toKnowledgeLinkFolderDo(folder.setParentId(parentId), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String userId, Long id) {
        ownedFolderForUpdate(userId, id);
        boolean hasChildren = agentKnowledgeLinkFolderIService.lambdaQuery()
                .eq(AgentKnowledgeLinkFolder::getScope, AgentKnowledgeLinkScope.USER)
                .eq(AgentKnowledgeLinkFolder::getOwnerUserId, userId)
                .eq(AgentKnowledgeLinkFolder::getParentId, id)
                .exists();
        // 禁用文档同样占用目录，防止重新启用后成为孤立文档。
        boolean hasLinks = agentKnowledgeLinkIService.lambdaQuery()
                .eq(AgentKnowledgeLink::getScope, AgentKnowledgeLinkScope.USER)
                .eq(AgentKnowledgeLink::getOwnerUserId, userId)
                .eq(AgentKnowledgeLink::getFolderId, id)
                .exists();
        if (hasChildren || hasLinks) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_NOT_EMPTY);
        }
        boolean deleted = agentKnowledgeLinkFolderIService.lambdaUpdate()
                .eq(AgentKnowledgeLinkFolder::getId, id)
                .eq(AgentKnowledgeLinkFolder::getScope, AgentKnowledgeLinkScope.USER)
                .eq(AgentKnowledgeLinkFolder::getOwnerUserId, userId)
                .remove();
        if (!deleted) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_NOT_FOUND);
        }
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockOwned(String userId, Long id) {
        if (id == null || id == KnowledgeLinkConstant.ROOT_FOLDER_ID) {
            return;
        }
        ownedFolderForUpdate(userId, id);
    }

    @Override
    public List<KnowledgeLinkFolderDo> listPublic() {
        return agentKnowledgeLinkFolderIService.lambdaQuery()
                .eq(AgentKnowledgeLinkFolder::getScope, AgentKnowledgeLinkScope.PUBLIC)
                .orderByAsc(AgentKnowledgeLinkFolder::getName)
                .list().stream()
                .map(this::toPublicKnowledgeLinkFolderDo)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeLinkFolderDo createPublic(String actorUserId, Long parentId, String name) {
        String normalizedName = normalizeName(name);
        long targetParentId = parentId == null ? KnowledgeLinkConstant.ROOT_FOLDER_ID : parentId;
        lockPublic(targetParentId);
        AgentKnowledgeLinkFolder folder = new AgentKnowledgeLinkFolder()
                .setId(snowflake.nextId())
                .setParentId(targetParentId)
                .setName(normalizedName)
                .setScope(AgentKnowledgeLinkScope.PUBLIC)
                .setOwnerUserId("")
                .setCreatedBy(actorUserId);
        try {
            if (!agentKnowledgeLinkFolderIService.save(folder)) {
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_INVALID);
            }
        } catch (DuplicateKeyException error) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_CONFLICT);
        }
        return toPublicKnowledgeLinkFolderDo(folder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeLinkFolderDo renamePublic(Long id, String name) {
        String normalizedName = normalizeName(name);
        AgentKnowledgeLinkFolder folder = publicFolderForUpdate(id);
        try {
            boolean updated = agentKnowledgeLinkFolderIService.lambdaUpdate()
                    .eq(AgentKnowledgeLinkFolder::getId, id)
                    .eq(AgentKnowledgeLinkFolder::getScope, AgentKnowledgeLinkScope.PUBLIC)
                    .set(AgentKnowledgeLinkFolder::getName, normalizedName)
                    .update();
            if (!updated) {
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_NOT_FOUND);
            }
        } catch (DuplicateKeyException error) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_CONFLICT);
        }
        return toPublicKnowledgeLinkFolderDo(folder.setName(normalizedName));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePublic(Long id) {
        publicFolderForUpdate(id);
        boolean hasChildren = agentKnowledgeLinkFolderIService.lambdaQuery()
                .eq(AgentKnowledgeLinkFolder::getScope, AgentKnowledgeLinkScope.PUBLIC)
                .eq(AgentKnowledgeLinkFolder::getParentId, id)
                .exists();
        boolean hasLinks = agentKnowledgeLinkIService.lambdaQuery()
                .eq(AgentKnowledgeLink::getScope, AgentKnowledgeLinkScope.PUBLIC)
                .eq(AgentKnowledgeLink::getFolderId, id)
                .exists();
        if (hasChildren || hasLinks) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_NOT_EMPTY);
        }
        boolean deleted = agentKnowledgeLinkFolderIService.lambdaUpdate()
                .eq(AgentKnowledgeLinkFolder::getId, id)
                .eq(AgentKnowledgeLinkFolder::getScope, AgentKnowledgeLinkScope.PUBLIC)
                .remove();
        if (!deleted) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_NOT_FOUND);
        }
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockPublic(Long id) {
        if (id == null || id == KnowledgeLinkConstant.ROOT_FOLDER_ID) {
            return;
        }
        publicFolderForUpdate(id);
    }

    /**
     * 查询并锁定个人文件夹；仅在写事务内调用，所有写操作使用相同的目录锁协议。
     */
    private AgentKnowledgeLinkFolder ownedFolderForUpdate(String userId, Long id) {
        if (id == null || id <= KnowledgeLinkConstant.ROOT_FOLDER_ID) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_INVALID);
        }
        AgentKnowledgeLinkFolder folder = agentKnowledgeLinkFolderIService.findForUpdate(id,
                AgentKnowledgeLinkScope.USER, userId);
        if (folder == null) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_NOT_FOUND);
        }
        return folder;
    }

    /**
     * 锁住目标到根目录的整条父链，防止并发移动改变祖先关系，并阻止自引用及循环目录。
     */
    private void lockTargetAncestors(String userId, Long sourceId, Long parentId) {
        Set<Long> visited = new HashSet<>();
        Long ancestorId = parentId;
        while (ancestorId != null && ancestorId != KnowledgeLinkConstant.ROOT_FOLDER_ID) {
            if (sourceId.equals(ancestorId) || !visited.add(ancestorId)) {
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_INVALID);
            }
            ancestorId = ownedFolderForUpdate(userId, ancestorId).getParentId();
        }
    }

    /**
     * 查询并锁定公共在线知识库文件夹。
     */
    private AgentKnowledgeLinkFolder publicFolderForUpdate(Long id) {
        if (id == null || id <= KnowledgeLinkConstant.ROOT_FOLDER_ID) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_INVALID);
        }
        AgentKnowledgeLinkFolder folder = agentKnowledgeLinkFolderIService.findForUpdate(id,
                AgentKnowledgeLinkScope.PUBLIC, null);
        if (folder == null) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_NOT_FOUND);
        }
        return folder;
    }

    /**
     * 规范化文件夹名称，限制空名称及超长名称。
     */
    private String normalizeName(String name) {
        String normalizedName = StringUtil.trim(name);
        if (StringUtil.isBlank(normalizedName) || normalizedName.length() > KnowledgeLinkConstant.MAX_NAME_LENGTH) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_FOLDER_INVALID);
        }
        return normalizedName;
    }

    /**
     * 转换为前端目录元数据，不暴露所属用户等内部字段。
     */
    private KnowledgeLinkFolderDo toKnowledgeLinkFolderDo(AgentKnowledgeLinkFolder folder, String userId) {
        return new KnowledgeLinkFolderDo()
                .setId(folder.getId())
                .setParentId(folder.getParentId())
                .setName(folder.getName())
                .setScope(folder.getScope())
                .setEditable(folder.getScope() == AgentKnowledgeLinkScope.USER && userId.equals(folder.getOwnerUserId()));
    }

    /**
     * 转换公共文件夹为管理员可编辑结构。
     */
    private KnowledgeLinkFolderDo toPublicKnowledgeLinkFolderDo(AgentKnowledgeLinkFolder folder) {
        return new KnowledgeLinkFolderDo()
                .setId(folder.getId())
                .setParentId(folder.getParentId())
                .setName(folder.getName())
                .setScope(folder.getScope())
                .setEditable(true);
    }
}
