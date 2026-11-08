package com.x.ai.skill;

import io.agentscope.core.skill.repository.AgentSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MysqlPublicSkillRepositoryProvider implements PublicSkillRepositoryProvider {

    private final PublicSkillRepositoryFactory repositoryFactory;

    @Override
    public List<AgentSkillRepository> repositories() {
        return List.of(repositoryFactory.create(false));
    }
}
