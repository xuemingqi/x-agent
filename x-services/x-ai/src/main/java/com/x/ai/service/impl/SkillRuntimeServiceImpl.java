package com.x.ai.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.x.ai.common.constants.AgentConstant;
import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.common.model.SkillRuntimeSnapshot;
import com.x.framework.common.utils.JsonUtil;
import com.x.ai.service.SkillRuntimeService;
import com.x.ai.service.UserSkillService;
import com.x.ai.skill.SkillSnapshotRepository;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class SkillRuntimeServiceImpl implements SkillRuntimeService {

    private final UserSkillService userSkillService;


    @Override
    public SkillRuntimeSnapshot load(String userId, ResourceEnablement enablement) {
        List<AgentSkill> skills = userSkillService.listEnabled(userId, enablement);
        List<AgentSkillRepository> repositories = List.of(
                new SkillSnapshotRepository(AgentConstant.USER_SKILL_SOURCE, skills));
        return new SkillRuntimeSnapshot(repositories, calculateFingerprint(repositories));
    }

    /**
     * 内容变化即替换运行时，编辑、导入和删除不依赖本机缓存失效通知。
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
