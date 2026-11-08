package com.x.ai.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.x.ai.common.domain.SkillShareDo;
import com.x.ai.common.model.SkillShareCreateParam;
import com.x.ai.db.entity.AgentSharedSkill;
import com.x.ai.db.entity.AgentSharedSkillResource;
import com.x.ai.db.entity.AgentSkillShare;
import com.x.ai.db.enums.AgentSkillShareStatus;
import com.x.ai.db.service.AgentSharedSkillIService;
import com.x.ai.db.service.AgentSharedSkillResourceIService;
import com.x.ai.db.service.AgentSkillShareIService;
import com.x.api.contact.api.UserApi;
import com.x.api.contact.domain.UserDo;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.service.SkillShareManagementService;
import com.x.ai.service.UserSkillService;
import io.agentscope.core.skill.AgentSkill;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SkillShareManagementServiceImpl implements SkillShareManagementService {

    private final UserApi userApi;

    private final UserSkillService userSkillService;

    private final AgentSkillShareIService agentSkillShareIService;

    private final AgentSharedSkillIService agentSharedSkillIService;

    private final AgentSharedSkillResourceIService agentSharedSkillResourceIService;

    private final Snowflake snowflake;

    private final Validator validator;


    @Override
    @Transactional
    public SkillShareDo share(String senderUserId, String skillName, SkillShareCreateParam request) {
        requireRuntimeUser(senderUserId);
        if (StringUtil.isBlank(skillName) || request == null || !validator.validate(request).isEmpty()) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_INVALID);
        }
        UserDo sender = requireUser(userApi.getUserById(StringUtil.parseLongId(senderUserId)));
        UserDo recipient = requireUser(userApi.getUserInfo(request.getRecipientMobile()));
        if (StringUtil.isBlank(sender.getMobile()) || senderUserId.equals(recipient.getId().toString())) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_INVALID);
        }

        AgentSkill sourceSkill = userSkillService.get(senderUserId, skillName);
        if (sourceSkill == null) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_NOT_FOUND);
        }

        AgentSkillShare existing = findExistingShare(senderUserId, recipient.getId().toString(), skillName);
        if (existing != null && existing.getStatus() != AgentSkillShareStatus.REJECTED) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_SHARE_CONFLICT);
        }

        AgentSharedSkill snapshot = saveSnapshot(senderUserId, sourceSkill);
        AgentSkillShare share = existing == null
                ? createShare(snapshot, sender, recipient)
                : reopenShare(existing, snapshot, sender, recipient);
        return toShareDo(share, snapshot, sourceSkill.getResourcePaths().stream().sorted().toList());
    }

    @Override
    public List<SkillShareDo> listPending(String userId) {
        requireRuntimeUser(userId);
        List<AgentSkillShare> shares = pendingShares(userId);
        if (shares.isEmpty()) {
            return List.of();
        }

        List<Long> skillIds = shares.stream().map(AgentSkillShare::getSkillId).toList();
        Map<Long, AgentSharedSkill> skills = agentSharedSkillIService.listByIds(skillIds).stream()
                .collect(Collectors.toMap(AgentSharedSkill::getId, Function.identity()));
        Map<Long, List<String>> resourcePaths = agentSharedSkillResourceIService.lambdaQuery()
                .in(AgentSharedSkillResource::getSkillId, skillIds)
                .list().stream()
                .collect(Collectors.groupingBy(AgentSharedSkillResource::getSkillId,
                        Collectors.mapping(AgentSharedSkillResource::getResourcePath, Collectors.toList())));

        return shares.stream()
                .map(share -> toShareDo(share, skills.get(share.getSkillId()),
                        resourcePaths.getOrDefault(share.getSkillId(), List.of()).stream().sorted().toList()))
                .toList();
    }

    @Override
    @Transactional
    public boolean accept(String userId, Long shareId) {
        return respond(userId, shareId, AgentSkillShareStatus.ACCEPTED);
    }

    @Override
    @Transactional
    public boolean reject(String userId, Long shareId) {
        return respond(userId, shareId, AgentSkillShareStatus.REJECTED);
    }

    /**
     * 异步入口必须提供平台可信身份，不能回退到请求线程身份。
     */
    private void requireRuntimeUser(String userId) {
        if (StringUtil.isBlank(userId)) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
    }

    /**
     * 查询同一发送人、接收人和个人 Skill 的现有分享关系。
     */
    private AgentSkillShare findExistingShare(String senderUserId, String recipientUserId, String skillName) {
        return agentSkillShareIService.lambdaQuery()
                .eq(AgentSkillShare::getSenderUserId, senderUserId)
                .eq(AgentSkillShare::getRecipientUserId, recipientUserId)
                .eq(AgentSkillShare::getSourceSkillName, skillName)
                .one();
    }

    /**
     * 保存个人 Skill 的不可变正文与资源快照。
     */
    private AgentSharedSkill saveSnapshot(String ownerUserId, AgentSkill sourceSkill) {
        AgentSharedSkill snapshot = new AgentSharedSkill()
                .setId(snowflake.nextId())
                .setOwnerUserId(ownerUserId)
                .setName(sourceSkill.getName())
                .setDescription(sourceSkill.getDescription())
                .setSkillContent(sourceSkill.getSkillContent());
        agentSharedSkillIService.save(snapshot);

        List<AgentSharedSkillResource> resources = sourceSkill.getResources().entrySet().stream()
                .map(entry -> new AgentSharedSkillResource()
                        .setId(snowflake.nextId())
                        .setSkillId(snapshot.getId())
                        .setResourcePath(entry.getKey())
                        .setResourceContent(entry.getValue()))
                .toList();
        if (!resources.isEmpty()) {
            agentSharedSkillResourceIService.saveBatch(resources);
        }
        return snapshot;
    }

    /**
     * 创建首次发送的待处理邀请。
     */
    private AgentSkillShare createShare(AgentSharedSkill snapshot, UserDo sender, UserDo recipient) {
        AgentSkillShare share = new AgentSkillShare()
                .setId(snowflake.nextId())
                .setSkillId(snapshot.getId())
                .setSenderUserId(sender.getId().toString())
                .setSenderMobile(sender.getMobile())
                .setSenderDisplayName(sender.getName())
                .setRecipientUserId(recipient.getId().toString())
                .setRecipientMobile(recipient.getMobile())
                .setSourceSkillName(snapshot.getName())
                .setStatus(AgentSkillShareStatus.PENDING);
        try {
            agentSkillShareIService.save(share);
        } catch (DuplicateKeyException error) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_SHARE_CONFLICT);
        }
        return share;
    }

    /**
     * 使用最新快照重新打开曾被拒绝的邀请，并清理无人引用的旧快照。
     */
    private AgentSkillShare reopenShare(AgentSkillShare share, AgentSharedSkill snapshot, UserDo sender, UserDo recipient) {
        Long previousSkillId = share.getSkillId();
        boolean reopened = agentSkillShareIService.lambdaUpdate()
                .eq(AgentSkillShare::getId, share.getId())
                .eq(AgentSkillShare::getStatus, AgentSkillShareStatus.REJECTED)
                .set(AgentSkillShare::getSkillId, snapshot.getId())
                .set(AgentSkillShare::getSenderMobile, sender.getMobile())
                .set(AgentSkillShare::getSenderDisplayName, sender.getName())
                .set(AgentSkillShare::getRecipientMobile, recipient.getMobile())
                .set(AgentSkillShare::getStatus, AgentSkillShareStatus.PENDING)
                .set(AgentSkillShare::getResponseTime, null)
                .update();
        if (!reopened) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_SHARE_STATUS_CONFLICT);
        }
        share.setSkillId(snapshot.getId())
                .setSenderMobile(sender.getMobile())
                .setSenderDisplayName(sender.getName())
                .setRecipientMobile(recipient.getMobile())
                .setStatus(AgentSkillShareStatus.PENDING)
                .setResponseTime(null);
        agentSharedSkillResourceIService.lambdaUpdate()
                .eq(AgentSharedSkillResource::getSkillId, previousSkillId)
                .remove();
        agentSharedSkillIService.removeById(previousSkillId);
        return share;
    }

    /**
     * 查询当前用户待处理邀请并按最新分享优先排序。
     */
    private List<AgentSkillShare> pendingShares(String userId) {
        return agentSkillShareIService.lambdaQuery()
                .eq(AgentSkillShare::getRecipientUserId, userId)
                .eq(AgentSkillShare::getStatus, AgentSkillShareStatus.PENDING)
                .orderByDesc(AgentSkillShare::getCreateTime)
                .list();
    }

    /**
     * 仅允许接收人对仍处于待处理状态的邀请作出一次响应。
     */
    private boolean respond(String userId, Long shareId, AgentSkillShareStatus targetStatus) {
        requireRuntimeUser(userId);
        AgentSkillShare share = agentSkillShareIService.lambdaQuery()
                .eq(AgentSkillShare::getId, shareId)
                .eq(AgentSkillShare::getRecipientUserId, userId)
                .one();
        if (share == null) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_SHARE_NOT_FOUND);
        }
        if (share.getStatus() != AgentSkillShareStatus.PENDING) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_SHARE_STATUS_CONFLICT);
        }
        if (targetStatus == AgentSkillShareStatus.ACCEPTED && hasAcceptedSameName(userId, share)) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_SHARE_NAME_CONFLICT);
        }

        boolean updated;
        try {
            updated = agentSkillShareIService.lambdaUpdate()
                    .eq(AgentSkillShare::getId, shareId)
                    .eq(AgentSkillShare::getRecipientUserId, userId)
                    .eq(AgentSkillShare::getStatus, AgentSkillShareStatus.PENDING)
                    .set(AgentSkillShare::getStatus, targetStatus)
                    .set(AgentSkillShare::getResponseTime, LocalDateTime.now())
                    .update();
        } catch (DuplicateKeyException error) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_SHARE_NAME_CONFLICT);
        }
        if (!updated) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_SHARE_STATUS_CONFLICT);
        }
        return true;
    }

    /**
     * 防止同一用户接受多个同名共享 Skill，确保运行时名称唯一。
     */
    private boolean hasAcceptedSameName(String userId, AgentSkillShare pendingShare) {
        return agentSkillShareIService.lambdaQuery()
                .eq(AgentSkillShare::getRecipientUserId, userId)
                .eq(AgentSkillShare::getSourceSkillName, pendingShare.getSourceSkillName())
                .eq(AgentSkillShare::getStatus, AgentSkillShareStatus.ACCEPTED)
                .ne(AgentSkillShare::getId, pendingShare.getId())
                .exists();
    }

    /**
     * 联系人服务负责解析账户；分享服务只使用可信用户标识与联系方式。
     */
    private UserDo requireUser(BaseResult<UserDo> result) {
        if (result != null && result.getCode() == ResponseCodeEnum.USER_NOT_EXIST.getCode()) {
            throw new XException(ResponseCodeEnum.USER_NOT_EXIST);
        }
        if (result == null || result.getCode() != ResponseCodeEnum.SUCCESS.getCode()
                || result.getData() == null || result.getData().getId() == null) {
            throw new XException(ResponseCodeEnum.INTERNAL_SERVER_ERROR);
        }
        return result.getData();
    }

    /**
     * 构建稳定的 Skill 分享邀请接口模型。
     */
    private SkillShareDo toShareDo(AgentSkillShare share, AgentSharedSkill skill, List<String> resourcePaths) {
        String senderMobile = share.getSenderMobile();
        String senderDisplayName = share.getSenderDisplayName();
        return new SkillShareDo()
                .setId(String.valueOf(share.getId()))
                .setSkillName(skill.getName())
                .setDescription(skill.getDescription())
                .setSenderMobile(senderMobile)
                .setSenderDisplayName(StringUtil.isBlank(senderDisplayName) ? senderMobile : senderDisplayName)
                .setStatus(share.getStatus())
                .setResourcePaths(resourcePaths)
                .setCreateTime(share.getCreateTime());
    }
}
