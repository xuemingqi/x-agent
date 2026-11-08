package com.x.ai.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.x.ai.common.constants.AgentConstant;
import com.x.ai.common.enums.SkillScope;
import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.common.model.SkillRuntimeSnapshot;
import com.x.framework.common.utils.JsonUtil;
import com.x.ai.service.SkillRuntimeService;
import com.x.ai.service.UserSkillService;
import com.x.ai.skill.PublicSkillRepositoryProvider;
import com.x.ai.skill.SharedSkillRepositoryFactory;
import com.x.ai.skill.SkillSnapshotRepository;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class SkillRuntimeServiceImpl implements SkillRuntimeService {

    private final List<PublicSkillRepositoryProvider> publicSkillRepositoryProviders;

    private final SharedSkillRepositoryFactory sharedSkillRepositoryFactory;

    private final UserSkillService userSkillService;


    @Override
    public SkillRuntimeSnapshot load(String userId, ResourceEnablement enablement) {
        List<AgentSkillRepository> snapshots = new ArrayList<>(loadPublicSnapshots(enablement));
        snapshots.add(loadSharedSnapshot(userId, enablement));
        snapshots.add(new SkillSnapshotRepository(AgentConstant.USER_SKILL_SOURCE,
                userSkillService.listEnabled(userId, enablement)));
        return new SkillRuntimeSnapshot(snapshots, calculateFingerprint(snapshots));
    }

    /**
     * 复制当前用户可用的公共 Skill，读取结束后释放原仓库。
     */
    private List<AgentSkillRepository> loadPublicSnapshots(ResourceEnablement enablement) {
        List<AgentSkillRepository> publicRepositories = publicSkillRepositoryProviders.stream()
                .flatMap(provider -> provider.repositories().stream()).toList();
        try {
            return publicRepositories.stream().map(repository -> createSnapshot(repository.getSource(),
                    repository.getAllSkills(), SkillScope.PUBLIC, enablement)).toList();
        } finally {
            publicRepositories.forEach(AgentSkillRepository::close);
        }
    }

    /**
     * 将当前用户已接受的共享 Skill 复制为独立快照。
     */
    private AgentSkillRepository loadSharedSnapshot(String userId, ResourceEnablement enablement) {
        try (AgentSkillRepository shared = sharedSkillRepositoryFactory.create(userId)) {
            return createSnapshot(shared.getSource(), shared.getAllSkills(), SkillScope.SHARED, enablement);
        }
    }

    /**
     * 先按来源授权再交由框架合并，同名个人项禁用后仍可使用已启用的公共版本。
     */
    private AgentSkillRepository createSnapshot(String source, List<AgentSkill> skills, SkillScope scope,
                                                ResourceEnablement enablement) {
        List<AgentSkill> allowed = skills.stream().filter(skill -> enablement.isSkillEnabled(scope, skill.getName()))
                .sorted(Comparator.comparing(AgentSkill::getName)).toList();
        return new SkillSnapshotRepository(source, allowed);
    }

    /**
     * 内容变化即替换运行时，编辑、删除和接受分享不依赖本机缓存失效通知。
     */
    private String calculateFingerprint(List<AgentSkillRepository> repositories) {
        var content = repositories.stream().map(repository -> Map.of("source", repository.getSource(),
                "skills", repository.getAllSkills().stream().map(this::fingerprintContent).toList())).toList();
        return DigestUtil.sha256Hex(JsonUtil.toJsonStrOrThrow(content));
    }

    /**
     * 固定元数据和资源的键顺序，避免相同内容因 Map 遍历顺序变化而重建运行时。
     */
    private Map<String, Object> fingerprintContent(AgentSkill skill) {
        return Map.of("metadata", new TreeMap<>(skill.getMetadata()), "content", skill.getSkillContent(),
                "resources", new TreeMap<>(skill.getResources()));
    }
}
