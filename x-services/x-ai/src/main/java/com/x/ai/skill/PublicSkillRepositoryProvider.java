package com.x.ai.skill;

import io.agentscope.core.skill.repository.AgentSkillRepository;

import java.util.List;

public interface PublicSkillRepositoryProvider {

    /**
     * 创建可供智能体读取的公共 Skill 仓库，由调用方负责关闭。
     *
     * @return 只读 Skill 仓库列表
     */
    List<AgentSkillRepository> repositories();
}
