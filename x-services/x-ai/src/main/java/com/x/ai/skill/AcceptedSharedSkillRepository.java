package com.x.ai.skill;

import com.x.ai.common.constants.AgentConstant;
import com.x.ai.db.entity.AgentSharedSkill;
import com.x.ai.db.entity.AgentSharedSkillResource;
import com.x.ai.db.entity.AgentSkillShare;
import com.x.ai.db.enums.AgentSkillShareStatus;
import com.x.ai.db.service.AgentSharedSkillIService;
import com.x.ai.db.service.AgentSharedSkillResourceIService;
import com.x.ai.db.service.AgentSkillShareIService;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public final class AcceptedSharedSkillRepository implements AgentSkillRepository {

    private final String userId;

    private final AgentSkillShareIService agentSkillShareIService;

    private final AgentSharedSkillIService agentSharedSkillIService;

    private final AgentSharedSkillResourceIService agentSharedSkillResourceIService;

    private static final String REPOSITORY_SOURCE = "shared-marketplace";


    @Override
    public AgentSkill getSkill(String name) {
        return getAllSkills().stream()
                .filter(skill -> skill.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<String> getAllSkillNames() {
        return getAllSkills().stream().map(AgentSkill::getName).toList();
    }

    @Override
    public List<AgentSkill> getAllSkills() {
        List<AgentSkillShare> shares = agentSkillShareIService.lambdaQuery()
                .eq(AgentSkillShare::getRecipientUserId, userId)
                .eq(AgentSkillShare::getStatus, AgentSkillShareStatus.ACCEPTED)
                .orderByAsc(AgentSkillShare::getCreateTime)
                .list();
        if (shares.isEmpty()) {
            return List.of();
        }

        List<Long> skillIds = shares.stream().map(AgentSkillShare::getSkillId).toList();
        Map<Long, AgentSharedSkill> skills = agentSharedSkillIService.listByIds(skillIds).stream()
                .collect(Collectors.toMap(AgentSharedSkill::getId, Function.identity()));
        Map<Long, Map<String, String>> resources = agentSharedSkillResourceIService.lambdaQuery()
                .in(AgentSharedSkillResource::getSkillId, skillIds)
                .list()
                .stream()
                .collect(Collectors.groupingBy(AgentSharedSkillResource::getSkillId, Collectors.toMap(AgentSharedSkillResource::getResourcePath, AgentSharedSkillResource::getResourceContent)));

        return shares.stream()
                .map(share -> toAgentSkill(share, skills.get(share.getSkillId()), resources.getOrDefault(share.getSkillId(), Map.of())))
                .toList();
    }

    @Override
    public boolean save(List<AgentSkill> skills, boolean overwrite) {
        return false;
    }

    @Override
    public boolean delete(String name) {
        return false;
    }

    @Override
    public boolean skillExists(String name) {
        return getSkill(name) != null;
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return new AgentSkillRepositoryInfo("mysql-shared", userId, false);
    }

    @Override
    public String getSource() {
        return REPOSITORY_SOURCE;
    }

    @Override
    public void setWriteable(boolean writeable) {
        if (writeable) {
            throw new UnsupportedOperationException("Shared Skill repository is read-only");
        }
    }

    @Override
    public boolean isWriteable() {
        return false;
    }

    /**
     * 将已接受分享的持久化快照转换为 AgentScope Skill。
     */
    private AgentSkill toAgentSkill(AgentSkillShare share, AgentSharedSkill skill, Map<String, String> resources) {
        return AgentSkill.builder()
                .name(skill.getName())
                .description(skill.getDescription())
                .skillContent(skill.getSkillContent())
                .resources(resources)
                .source(REPOSITORY_SOURCE)
                .putMetadata(AgentConstant.SHARED_SKILL_SENDER_MOBILE_METADATA, share.getSenderMobile())
                .build();
    }
}
