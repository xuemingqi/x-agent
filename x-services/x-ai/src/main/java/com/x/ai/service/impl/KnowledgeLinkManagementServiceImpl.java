package com.x.ai.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.x.ai.common.constants.KnowledgeLinkConstant;
import com.x.ai.common.domain.KnowledgeLinkCatalogDo;
import com.x.ai.common.domain.KnowledgeLinkDetailDo;
import com.x.ai.common.domain.KnowledgeLinkDo;
import com.x.ai.common.model.KnowledgeLinkParam;
import com.x.ai.db.entity.AgentKnowledgeLink;
import com.x.ai.db.enums.AgentKnowledgeLinkScope;
import com.x.ai.db.service.AgentKnowledgeLinkIService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.service.KnowledgeLinkManagementService;
import com.x.ai.service.KnowledgeLinkFolderManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KnowledgeLinkManagementServiceImpl implements KnowledgeLinkManagementService {

    private static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https");

    private final AgentKnowledgeLinkIService agentKnowledgeLinkIService;

    private final Snowflake snowflake;

    private final KnowledgeLinkFolderManagementService knowledgeLinkFolderManagementService;


    @Override
    public List<KnowledgeLinkDo> list(String userId) {
        return agentKnowledgeLinkIService.lambdaQuery()
                .eq(AgentKnowledgeLink::getEnabled, true)
                .eq(AgentKnowledgeLink::getScope, AgentKnowledgeLinkScope.USER)
                .eq(AgentKnowledgeLink::getOwnerUserId, userId)
                .orderByAsc(AgentKnowledgeLink::getName)
                .list()
                .stream()
                .map(link -> toKnowledgeLinkDo(link, userId))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeLinkDo create(String userId, KnowledgeLinkParam param) {
        KnowledgeLinkParam normalized = normalize(param);
        Long folderId = normalized.getFolderId() == null ? KnowledgeLinkConstant.ROOT_FOLDER_ID : normalized.getFolderId();
        knowledgeLinkFolderManagementService.lockOwned(userId, folderId);
        AgentKnowledgeLink link = new AgentKnowledgeLink()
                .setId(snowflake.nextId())
                .setFolderId(folderId)
                .setName(normalized.getName())
                .setSummary(normalized.getSummary())
                .setUrl(normalized.getUrl())
                .setUrlHash(StringUtil.sha256Hex(normalized.getUrl()))
                .setScope(AgentKnowledgeLinkScope.USER)
                .setOwnerUserId(userId)
                .setEnabled(true)
                .setCreatedBy(userId);
        try {
            if (!agentKnowledgeLinkIService.save(link)) {
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_INVALID);
            }
        } catch (DuplicateKeyException error) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_CONFLICT);
        }
        return toKnowledgeLinkDo(link, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeLinkDo update(String userId, Long id, KnowledgeLinkParam param) {
        return updateOwned(userId, id, normalize(param));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeLinkDo patch(String userId, Long id, KnowledgeLinkParam param) {
        if (param == null || param.getName() == null && param.getSummary() == null
                && param.getUrl() == null && param.getFolderId() == null) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_INVALID);
        }
        KnowledgeLinkParam normalized = new KnowledgeLinkParam()
                .setName(param.getName() == null ? null : normalizeText(param.getName(), KnowledgeLinkConstant.MAX_NAME_LENGTH))
                .setSummary(param.getSummary() == null ? null
                        : normalizeText(param.getSummary(), KnowledgeLinkConstant.MAX_SUMMARY_LENGTH))
                .setUrl(param.getUrl() == null ? null : normalizeUrl(param.getUrl()))
                .setFolderId(param.getFolderId());
        return updateOwned(userId, id, normalized);
    }

    /**
     * 只写入本次明确提供的字段，避免部分编辑或移动覆盖其他并发修改。
     */
    private KnowledgeLinkDo updateOwned(String userId, Long id, KnowledgeLinkParam normalized) {
        knowledgeLinkFolderManagementService.lockOwned(userId, normalized.getFolderId());
        try {
            boolean updated = agentKnowledgeLinkIService.lambdaUpdate()
                    .eq(AgentKnowledgeLink::getId, id)
                    .eq(AgentKnowledgeLink::getScope, AgentKnowledgeLinkScope.USER)
                    .eq(AgentKnowledgeLink::getOwnerUserId, userId)
                    .set(normalized.getName() != null, AgentKnowledgeLink::getName, normalized.getName())
                    .set(normalized.getSummary() != null, AgentKnowledgeLink::getSummary, normalized.getSummary())
                    .set(normalized.getUrl() != null, AgentKnowledgeLink::getUrl, normalized.getUrl())
                    .set(normalized.getUrl() != null, AgentKnowledgeLink::getUrlHash, normalized.getUrl() == null ? null : StringUtil.sha256Hex(normalized.getUrl()))
                    .set(normalized.getFolderId() != null, AgentKnowledgeLink::getFolderId, normalized.getFolderId())
                    .update();
            if (!updated) {
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_NOT_FOUND);
            }
        } catch (DuplicateKeyException error) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_CONFLICT);
        }
        return toKnowledgeLinkDo(ownedLink(userId, id), userId);
    }

    @Override
    public boolean delete(String userId, Long id) {
        boolean deleted = agentKnowledgeLinkIService.lambdaUpdate()
                .eq(AgentKnowledgeLink::getId, id)
                .eq(AgentKnowledgeLink::getScope, AgentKnowledgeLinkScope.USER)
                .eq(AgentKnowledgeLink::getOwnerUserId, userId)
                .remove();
        if (!deleted) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_NOT_FOUND);
        }
        return true;
    }

    @Override
    public List<KnowledgeLinkCatalogDo> catalog(String userId) {
        return visibleQuery(userId)
                .select(AgentKnowledgeLink::getId, AgentKnowledgeLink::getName, AgentKnowledgeLink::getSummary)
                .orderByAsc(AgentKnowledgeLink::getScope)
                .orderByAsc(AgentKnowledgeLink::getName)
                .list()
                .stream()
                .map(link -> new KnowledgeLinkCatalogDo()
                        .setId(link.getId())
                        .setName(link.getName())
                        .setSummary(link.getSummary()))
                .toList();
    }

    @Override
    public KnowledgeLinkDetailDo getVisible(String userId, Long id) {
        AgentKnowledgeLink link = visibleQuery(userId)
                .eq(AgentKnowledgeLink::getId, id)
                .one();
        if (link == null) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_NOT_FOUND);
        }
        return new KnowledgeLinkDetailDo()
                .setId(link.getId())
                .setName(link.getName())
                .setUrl(link.getUrl());
    }

    @Override
    public List<KnowledgeLinkDo> listPublic() {
        return agentKnowledgeLinkIService.lambdaQuery()
                .eq(AgentKnowledgeLink::getEnabled, true)
                .eq(AgentKnowledgeLink::getScope, AgentKnowledgeLinkScope.PUBLIC)
                .orderByAsc(AgentKnowledgeLink::getName)
                .list().stream()
                .map(this::toPublicKnowledgeLinkDo)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeLinkDo createPublic(String actorUserId, KnowledgeLinkParam param) {
        KnowledgeLinkParam normalized = normalize(param);
        Long folderId = normalized.getFolderId() == null ? KnowledgeLinkConstant.ROOT_FOLDER_ID : normalized.getFolderId();
        knowledgeLinkFolderManagementService.lockPublic(folderId);
        AgentKnowledgeLink link = new AgentKnowledgeLink()
                .setId(snowflake.nextId())
                .setFolderId(folderId)
                .setName(normalized.getName())
                .setSummary(normalized.getSummary())
                .setUrl(normalized.getUrl())
                .setUrlHash(StringUtil.sha256Hex(normalized.getUrl()))
                .setScope(AgentKnowledgeLinkScope.PUBLIC)
                .setOwnerUserId("")
                .setEnabled(true)
                .setCreatedBy(actorUserId);
        try {
            if (!agentKnowledgeLinkIService.save(link)) {
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_INVALID);
            }
        } catch (DuplicateKeyException error) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_CONFLICT);
        }
        return toPublicKnowledgeLinkDo(link);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeLinkDo updatePublic(Long id, KnowledgeLinkParam param) {
        KnowledgeLinkParam normalized = normalize(param);
        knowledgeLinkFolderManagementService.lockPublic(normalized.getFolderId());
        try {
            boolean updated = agentKnowledgeLinkIService.lambdaUpdate()
                    .eq(AgentKnowledgeLink::getId, id)
                    .eq(AgentKnowledgeLink::getScope, AgentKnowledgeLinkScope.PUBLIC)
                    .set(AgentKnowledgeLink::getName, normalized.getName())
                    .set(AgentKnowledgeLink::getSummary, normalized.getSummary())
                    .set(AgentKnowledgeLink::getUrl, normalized.getUrl())
                    .set(AgentKnowledgeLink::getUrlHash, StringUtil.sha256Hex(normalized.getUrl()))
                    .set(normalized.getFolderId() != null, AgentKnowledgeLink::getFolderId, normalized.getFolderId())
                    .update();
            if (!updated) {
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_NOT_FOUND);
            }
        } catch (DuplicateKeyException error) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_CONFLICT);
        }
        return toPublicKnowledgeLinkDo(publicLink(id));
    }

    @Override
    public boolean deletePublic(Long id) {
        boolean deleted = agentKnowledgeLinkIService.lambdaUpdate()
                .eq(AgentKnowledgeLink::getId, id)
                .eq(AgentKnowledgeLink::getScope, AgentKnowledgeLinkScope.PUBLIC)
                .remove();
        if (!deleted) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_NOT_FOUND);
        }
        return true;
    }

    /**
     * 构建只包含公共链接和指定用户个人链接的启用资源查询。
     */
    private LambdaQueryChainWrapper<AgentKnowledgeLink> visibleQuery(String userId) {
        return agentKnowledgeLinkIService.lambdaQuery()
                .eq(AgentKnowledgeLink::getEnabled, true)
                .nested(visible -> visible.eq(AgentKnowledgeLink::getScope, AgentKnowledgeLinkScope.PUBLIC)
                        .or(user -> user.eq(AgentKnowledgeLink::getScope, AgentKnowledgeLinkScope.USER)
                                .eq(AgentKnowledgeLink::getOwnerUserId, userId)));
    }

    /**
     * 查询指定用户拥有的个人链接，公共链接始终只读。
     */
    private AgentKnowledgeLink ownedLink(String userId, Long id) {
        AgentKnowledgeLink link = agentKnowledgeLinkIService.lambdaQuery()
                .eq(AgentKnowledgeLink::getId, id)
                .eq(AgentKnowledgeLink::getScope, AgentKnowledgeLinkScope.USER)
                .eq(AgentKnowledgeLink::getOwnerUserId, userId)
                .one();
        if (link == null) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_NOT_FOUND);
        }
        return link;
    }

    /**
     * 查询必须存在的公共在线知识库链接。
     */
    private AgentKnowledgeLink publicLink(Long id) {
        AgentKnowledgeLink link = agentKnowledgeLinkIService.lambdaQuery()
                .eq(AgentKnowledgeLink::getId, id)
                .eq(AgentKnowledgeLink::getScope, AgentKnowledgeLinkScope.PUBLIC)
                .one();
        if (link == null) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_NOT_FOUND);
        }
        return link;
    }

    /**
     * 规范化用户输入并限制为不携带内嵌凭证的 HTTP(S) 绝对链接。
     */
    private KnowledgeLinkParam normalize(KnowledgeLinkParam param) {
        if (param == null) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_INVALID);
        }
        String name = normalizeText(param.getName(), KnowledgeLinkConstant.MAX_NAME_LENGTH);
        String summary = normalizeText(param.getSummary(), KnowledgeLinkConstant.MAX_SUMMARY_LENGTH);
        String url = normalizeUrl(param.getUrl());
        return new KnowledgeLinkParam().setName(name).setSummary(summary).setUrl(url).setFolderId(param.getFolderId());
    }

    /**
     * 统一完整配置与部分编辑的必填文本校验，显式空串不会被视为省略。
     */
    private String normalizeText(String value, int maxLength) {
        String normalized = StringUtil.trim(value);
        if (StringUtil.isBlank(normalized) || normalized.length() > maxLength) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_INVALID);
        }
        return normalized;
    }

    /**
     * 校验在线文档 URL 并消除无业务意义的路径片段差异。
     */
    private String normalizeUrl(String value) {
        if (StringUtil.isBlank(value)) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_INVALID);
        }
        try {
            URI uri = URI.create(StringUtil.trim(value)).normalize();
            String scheme = StringUtil.nullToEmpty(uri.getScheme()).toLowerCase(Locale.ROOT);
            if (!uri.isAbsolute() || !SUPPORTED_SCHEMES.contains(scheme) || StringUtil.isBlank(uri.getHost())
                    || uri.getUserInfo() != null) {
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_INVALID);
            }
            String normalized = uri.toASCIIString();
            if (normalized.length() > KnowledgeLinkConstant.MAX_URL_LENGTH) {
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_INVALID);
            }
            return normalized;
        } catch (IllegalArgumentException error) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_LINK_INVALID);
        }
    }

    /**
     * 将持久化链接转换为管理界面使用的领域对象。
     */
    private KnowledgeLinkDo toKnowledgeLinkDo(AgentKnowledgeLink link, String userId) {
        boolean editable = link.getScope() == AgentKnowledgeLinkScope.USER
                && userId.equals(link.getOwnerUserId());
        return new KnowledgeLinkDo()
                .setId(link.getId())
                .setFolderId(link.getFolderId())
                .setName(link.getName())
                .setSummary(link.getSummary())
                .setUrl(link.getUrl())
                .setScope(link.getScope())
                .setEditable(editable)
                .setCreateTime(link.getCreateTime())
                .setUpdateTime(link.getUpdateTime());
    }

    /**
     * 转换公共链接为管理员可编辑结构。
     */
    private KnowledgeLinkDo toPublicKnowledgeLinkDo(AgentKnowledgeLink link) {
        return new KnowledgeLinkDo()
                .setId(link.getId())
                .setFolderId(link.getFolderId())
                .setName(link.getName())
                .setSummary(link.getSummary())
                .setUrl(link.getUrl())
                .setScope(link.getScope())
                .setEditable(true)
                .setCreateTime(link.getCreateTime())
                .setUpdateTime(link.getUpdateTime());
    }
}
