package com.x.ai.service;

import com.x.ai.common.model.ResourceEnablement;
import io.agentscope.core.skill.AgentSkill;

import java.util.List;

public interface UserSkillService {

    /**
     * 管理入口读取指定用户的全部个人 Skill，包括已禁用项。
     */
    List<AgentSkill> list(String userId);

    /**
     * 运行时仅查询并装配指定用户已启用的个人 Skill。
     */
    List<AgentSkill> listEnabled(String userId, ResourceEnablement enablement);

    /**
     * 读取指定用户的个人 Skill，不存在时返回 null。
     */
    AgentSkill get(String userId, String name);

    /**
     * 原子保存正文及全部资源；overwrite=false 时拒绝覆盖同名 Skill。
     */
    AgentSkill save(String userId, AgentSkill skill, boolean overwrite);

    /**
     * 删除指定用户的个人 Skill 及资源，不存在时返回 false。
     */
    boolean delete(String userId, String name);
}
