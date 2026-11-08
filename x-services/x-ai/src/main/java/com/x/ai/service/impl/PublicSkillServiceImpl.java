package com.x.ai.service.impl;

import com.x.ai.common.domain.SkillDo;
import com.x.ai.common.dto.UserSkillCreateDto;
import com.x.ai.common.dto.UserSkillUpdateDto;
import com.x.ai.common.enums.SkillScope;
import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.converter.SkillConverter;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.ai.service.AdministratorAuthorizationService;
import com.x.ai.service.HarnessAgentService;
import com.x.ai.service.PublicSkillService;
import com.x.ai.service.PublicResourceVersionService;
import com.x.ai.service.ResourceEnablementService;
import com.x.ai.skill.PublicSkillRepositoryFactory;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PublicSkillServiceImpl implements PublicSkillService {

    private static final String PUBLIC_SKILL_SOURCE = "platform-public";

    private final AdministratorAuthorizationService authorizationService;

    private final PublicSkillRepositoryFactory repositoryFactory;

    private final HarnessAgentService harnessAgentService;

    private final PublicResourceVersionService publicResourceVersionService;

    private final ResourceEnablementService resourceEnablementService;


    @Override
    public List<SkillDo> list(String actorUserId) {
        authorizationService.requireAdministrator(actorUserId);
        ResourceEnablement enablement = resourceEnablementService.get(actorUserId);
        try (AgentSkillRepository repository = repositoryFactory.create(false)) {
            return repository.getAllSkills().stream()
                    .map(skill -> SkillConverter.toSkillDo(skill, SkillScope.PUBLIC, true, enablement))
                    .sorted(Comparator.comparing(SkillDo::getName))
                    .toList();
        }
    }

    @Override
    public SkillDo get(String actorUserId, String name) {
        authorizationService.requireAdministrator(actorUserId);
        try (AgentSkillRepository repository = repositoryFactory.create(false)) {
            return toSkillDo(actorUserId, requireSkill(repository, name));
        }
    }

    @Override
    public SkillDo create(String actorUserId, UserSkillCreateDto request) {
        authorizationService.requireAdministrator(actorUserId);
        try (AgentSkillRepository repository = repositoryFactory.create(true)) {
            if (repository.skillExists(request.getName())) {
                throw new XException(ResponseCodeEnum.AGENT_SKILL_CONFLICT);
            }
            AgentSkill skill = buildSkill(request.getName(), request.getDescription(), request.getContent(), Map.of());
            if (!repository.save(List.of(skill), false)) {
                throw new XException(ResponseCodeEnum.AGENT_SKILL_INVALID);
            }
            invalidateAgentRuntimes();
            return toSkillDo(actorUserId, skill);
        }
    }

    @Override
    public SkillDo update(String actorUserId, String name, UserSkillUpdateDto request) {
        authorizationService.requireAdministrator(actorUserId);
        try (AgentSkillRepository repository = repositoryFactory.create(true)) {
            AgentSkill current = requireSkill(repository, name);
            AgentSkill updated = buildSkill(name, request.getDescription(), request.getContent(), current.getResources());
            if (!repository.save(List.of(updated), true)) {
                throw new XException(ResponseCodeEnum.AGENT_SKILL_INVALID);
            }
            invalidateAgentRuntimes();
            return toSkillDo(actorUserId, updated);
        }
    }

    @Override
    public boolean delete(String actorUserId, String name) {
        authorizationService.requireAdministrator(actorUserId);
        try (AgentSkillRepository repository = repositoryFactory.create(true)) {
            if (!repository.delete(name)) {
                throw new XException(ResponseCodeEnum.AGENT_SKILL_NOT_FOUND);
            }
            invalidateAgentRuntimes();
            return true;
        }
    }

    /**
     * 推进集群版本，并立即淘汰本节点的 Agent。
     */
    private void invalidateAgentRuntimes() {
        publicResourceVersionService.advanceVersion();
        harnessAgentService.evictAllAgents();
    }

    /**
     * 读取必须存在的公共 Skill。
     */
    private AgentSkill requireSkill(AgentSkillRepository repository, String name) {
        AgentSkill skill = repository.getSkill(name);
        if (skill == null) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_NOT_FOUND);
        }
        return skill;
    }

    /**
     * 构建符合 AgentScope 格式的公共 Skill。
     */
    private AgentSkill buildSkill(String name, String description, String content, Map<String, String> resources) {
        return AgentSkill.builder()
                .name(name)
                .description(description.trim())
                .skillContent(content.trim())
                .resources(resources)
                .source(PUBLIC_SKILL_SOURCE)
                .build();
    }

    /**
     * 管理员的编辑权限与个人启用偏好分别参与转换，与普通用户入口保持相同语义。
     */
    private SkillDo toSkillDo(String actorUserId, AgentSkill skill) {
        return SkillConverter.toSkillDo(skill, SkillScope.PUBLIC, true, resourceEnablementService.get(actorUserId));
    }
}
