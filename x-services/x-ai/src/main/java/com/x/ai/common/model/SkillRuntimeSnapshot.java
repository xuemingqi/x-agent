package com.x.ai.common.model;

import io.agentscope.core.skill.repository.AgentSkillRepository;
import lombok.Value;

import java.util.List;

/**
 * 按当前用户授权筛选的只读 Skill 仓库及其内容摘要。
 */
@Value
public class SkillRuntimeSnapshot {

    /**
     * 已启用且获得入口授权的仓库，按公共、共享、个人顺序参与同名覆盖。
     */
    private List<AgentSkillRepository> repositories;

    /**
     * 仓库来源、元数据、正文和附件的摘要，用于判断运行时能否复用。
     */
    private final String fingerprint;

    public SkillRuntimeSnapshot(List<AgentSkillRepository> repositories, String fingerprint) {
        this.repositories = List.copyOf(repositories);
        this.fingerprint = fingerprint;
    }
}
