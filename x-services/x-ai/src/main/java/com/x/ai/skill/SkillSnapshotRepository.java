package com.x.ai.skill;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;

import java.util.List;

/**
 * 一轮运行的只读 Skill 快照；模型目录与沙箱文件始终来自同一份内容。
 */
public final class SkillSnapshotRepository implements AgentSkillRepository {

    private final String source;

    private final List<AgentSkill> skills;

    public SkillSnapshotRepository(String source, List<AgentSkill> skills) {
        this.source = source;
        this.skills = skills.stream().map(skill -> skill.toBuilder().originDir(null).build()).toList();
    }

    @Override
    public AgentSkill getSkill(String name) {
        return skills.stream().filter(skill -> skill.getName().equals(name)).findFirst().orElse(null);
    }

    @Override
    public List<AgentSkill> getAllSkills() {
        return skills;
    }

    @Override
    public List<String> getAllSkillNames() {
        return skills.stream().map(AgentSkill::getName).toList();
    }

    @Override
    public boolean skillExists(String name) {
        return getSkill(name) != null;
    }

    @Override
    public String getSource() {
        return source;
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return new AgentSkillRepositoryInfo("runtime-snapshot", source, false);
    }

    @Override
    public boolean isWriteable() {
        return false;
    }

    @Override
    public void setWriteable(boolean writeable) {
        if (writeable) {
            throw new UnsupportedOperationException("Runtime Skill snapshot is read-only");
        }
    }

    @Override
    public boolean save(List<AgentSkill> skills, boolean overwrite) {
        return false;
    }

    @Override
    public boolean delete(String name) {
        return false;
    }
}
