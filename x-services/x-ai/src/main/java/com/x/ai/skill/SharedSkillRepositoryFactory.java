package com.x.ai.skill;

import com.x.ai.db.service.AgentSharedSkillIService;
import com.x.ai.db.service.AgentSharedSkillResourceIService;
import com.x.ai.db.service.AgentSkillShareIService;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SharedSkillRepositoryFactory {

    private final AgentSkillShareIService agentSkillShareIService;

    private final AgentSharedSkillIService agentSharedSkillIService;

    private final AgentSharedSkillResourceIService agentSharedSkillResourceIService;


    /**
     * 绑定接收人身份，仅提供已接受分享的数据库快照。
     */
    public AgentSkillRepository create(String userId) {
        return new AcceptedSharedSkillRepository(userId, agentSkillShareIService, agentSharedSkillIService, agentSharedSkillResourceIService);
    }
}
