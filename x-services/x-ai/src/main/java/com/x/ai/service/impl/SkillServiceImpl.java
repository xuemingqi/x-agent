package com.x.ai.service.impl;

import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.ai.common.constants.AgentConstant;
import com.x.ai.common.domain.SkillDo;
import com.x.ai.common.dto.UserSkillCreateDto;
import com.x.ai.common.dto.UserSkillUpdateDto;
import com.x.ai.common.enums.SkillArchiveFormat;
import com.x.ai.common.enums.SkillScope;
import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.converter.SkillConverter;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.ai.service.ResourceEnablementService;
import com.x.ai.service.SkillArchiveService;
import com.x.ai.service.SkillService;
import com.x.ai.service.UserSkillService;
import com.x.ai.skill.PublicSkillRepositoryProvider;
import com.x.ai.skill.SharedSkillRepositoryFactory;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private final UserSkillService userSkillService;

    private final SharedSkillRepositoryFactory sharedSkillRepositoryFactory;

    private final List<PublicSkillRepositoryProvider> publicSkillRepositoryProviders;

    private final SkillArchiveService skillArchiveService;

    private final ResourceEnablementService resourceEnablementService;


    @Override
    public List<SkillDo> list(SkillScope scope) {
        ResourceEnablement enablement = resourceEnablementService.get(UserThreadLocalUtil.getUserId());
        List<SkillDo> skills = new ArrayList<>();
        if (scope == null || scope == SkillScope.PUBLIC) {
            skills.addAll(listPublicSkills().stream()
                    .map(skill -> SkillConverter.toSkillDo(skill, SkillScope.PUBLIC, false, enablement))
                    .toList());
        }
        if (scope == null || scope == SkillScope.USER) {
            skills.addAll(listUserSkills().stream()
                    .map(skill -> SkillConverter.toSkillDo(skill, SkillScope.USER, true, enablement))
                    .toList());
        }
        if (scope == null || scope == SkillScope.SHARED) {
            skills.addAll(listSharedSkills().stream()
                    .map(skill -> SkillConverter.toSkillDo(skill, SkillScope.SHARED, false, enablement))
                    .toList());
        }
        return skills.stream()
                .sorted(Comparator.comparing(SkillDo::getScope).thenComparing(SkillDo::getName))
                .toList();
    }

    @Override
    public boolean setEnabled(SkillScope scope, String name, boolean enabled) {
        requireSkill(scope, name);
        resourceEnablementService.setEnabled(UserThreadLocalUtil.getUserId(),
                ResourceEnablement.skillKey(scope, name), enabled);
        return enabled;
    }

    @Override
    public SkillDo get(SkillScope scope, String name) {
        return toSkillDo(requireSkill(scope, name), scope);
    }

    /**
     * 导出和详情共用作用域校验，只访问当前用户可见的 Skill。
     */
    private AgentSkill requireSkill(SkillScope scope, String name) {
        if (scope == null) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_INVALID);
        }
        AgentSkill skill = switch (scope) {
            case PUBLIC -> findPublicSkill(name);
            case USER -> getUserSkill(name);
            case SHARED -> getSharedSkill(name);
        };
        if (skill == null) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_NOT_FOUND);
        }
        return skill;
    }

    @Override
    public SkillDo importArchive(MultipartFile file) {
        AgentSkill skill = skillArchiveService.read(file);
        return toSkillDo(userSkillService.save(currentUserId(), skill, false), SkillScope.USER);
    }

    @Override
    public byte[] exportArchive(SkillScope scope, String name, SkillArchiveFormat format) {
        return skillArchiveService.write(requireSkill(scope, name), format);
    }

    @Override
    public SkillDo create(UserSkillCreateDto request) {
        AgentSkill skill = buildSkill(request.getName(), request.getDescription(), request.getContent(), Map.of());
        return toSkillDo(userSkillService.save(currentUserId(), skill, false), SkillScope.USER);
    }

    @Override
    public SkillDo update(String name, UserSkillUpdateDto request) {
        AgentSkill current = requireSkill(SkillScope.USER, name);
        AgentSkill updated = current.toBuilder().description(request.getDescription().trim())
                .skillContent(request.getContent().trim()).build();
        return toSkillDo(userSkillService.save(currentUserId(), updated, true), SkillScope.USER);
    }

    @Override
    public boolean delete(String name) {
        if (!userSkillService.delete(currentUserId(), name)) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_NOT_FOUND);
        }
        return true;
    }

    /**
     * 管理接口只从已认证上下文取得所有者身份。
     */
    private String currentUserId() {
        return UserThreadLocalUtil.getUserId();
    }

    /**
     * 汇总所有公共 Skill 仓库，管理接口与 Agent 运行时复用同一扩展入口。
     */
    private List<AgentSkillRepository> publicRepositories() {
        return publicSkillRepositoryProviders.stream()
                .flatMap(provider -> provider.repositories().stream())
                .toList();
    }

    /**
     * 从公共 Skill 仓库中查询第一个同名 Skill。
     */
    private AgentSkill findPublicSkill(String name) {
        List<AgentSkillRepository> repositories = publicRepositories();
        try {
            return repositories.stream()
                    .map(repository -> repository.getSkill(name))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } finally {
            repositories.forEach(AgentSkillRepository::close);
        }
    }

    /**
     * 管理查询只读取 Skill 快照，读取完成后释放本次创建的公共仓库。
     */
    private List<AgentSkill> listPublicSkills() {
        List<AgentSkillRepository> repositories = publicRepositories();
        try {
            return repositories.stream().flatMap(repository -> repository.getAllSkills().stream()).toList();
        } finally {
            repositories.forEach(AgentSkillRepository::close);
        }
    }

    /**
     * 管理列表包含禁用项，以便编辑和重新启用。
     */
    private List<AgentSkill> listUserSkills() {
        return userSkillService.list(currentUserId());
    }

    /**
     * 详情和导出共用数据库中的完整 Skill 包。
     */
    private AgentSkill getUserSkill(String name) {
        return userSkillService.get(currentUserId(), name);
    }

    /**
     * 在仓库生命周期内读取当前用户已接受的全部共享 Skill。
     */
    private List<AgentSkill> listSharedSkills() {
        try (AgentSkillRepository repository = createSharedRepository()) {
            return repository.getAllSkills();
        }
    }

    /**
     * 在仓库生命周期内读取当前用户已接受的指定共享 Skill。
     */
    private AgentSkill getSharedSkill(String name) {
        try (AgentSkillRepository repository = createSharedRepository()) {
            return repository.getSkill(name);
        }
    }

    /**
     * 创建当前用户只读的共享 Skill 仓库，由调用方负责关闭。
     */
    private AgentSkillRepository createSharedRepository() {
        return sharedSkillRepositoryFactory.create(UserThreadLocalUtil.getUserId());
    }

    /**
     * 构建符合 AgentScope SKILL.md 格式的 Skill，并保留附属文件内容。
     */
    private AgentSkill buildSkill(String name, String description, String content, Map<String, String> resources) {
        return AgentSkill.builder()
                .name(name)
                .description(description.trim())
                .skillContent(content.trim())
                .resources(resources)
                .source(AgentConstant.USER_SKILL_SOURCE)
                .build();
    }

    /**
     * 单条读写结果也使用当前用户的启用状态；列表转换则复用一次批量读取的快照。
     */
    private SkillDo toSkillDo(AgentSkill skill, SkillScope scope) {
        ResourceEnablement enablement = resourceEnablementService.get(UserThreadLocalUtil.getUserId());
        return SkillConverter.toSkillDo(skill, scope, scope == SkillScope.USER, enablement);
    }
}
