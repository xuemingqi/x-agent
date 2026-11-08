package com.x.ai.skill;

import com.x.ai.properties.AgentProperties;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.mysql.MysqlSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@RequiredArgsConstructor
public class PublicSkillRepositoryFactory {

    private final DataSource dataSource;

    private final AgentProperties agentProperties;

    /**
     * 创建使用平台公共 Skill 表的仓库。
     *
     * @param writeable 是否允许写入
     * @return 公共 Skill 仓库
     */
    public AgentSkillRepository create(boolean writeable) {
        AgentProperties.Skill skill = agentProperties.getSkill();
        return MysqlSkillRepository.builder(dataSource)
                .databaseName(skill.getDatabaseName())
                .skillsTableName(skill.getTableName())
                .resourcesTableName(skill.getResourceTableName())
                .createIfNotExist(false)
                .writeable(writeable)
                .build();
    }
}
