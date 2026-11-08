package com.x.ai.service;

import com.x.ai.common.domain.SkillDo;
import com.x.ai.common.dto.UserSkillCreateDto;
import com.x.ai.common.dto.UserSkillUpdateDto;

import java.util.List;

public interface PublicSkillService {

    /**
     * 查询公共 Skill。
     *
     * @param actorUserId 操作用户 ID
     * @return 公共 Skill 列表
     */
    List<SkillDo> list(String actorUserId);

    /**
     * 查询公共 Skill 详情。
     *
     * @param actorUserId 操作用户 ID
     * @param name        Skill 名称
     * @return 公共 Skill
     */
    SkillDo get(String actorUserId, String name);

    /**
     * 创建公共 Skill。
     *
     * @param actorUserId 操作用户 ID
     * @param request     Skill 配置
     * @return 已创建 Skill
     */
    SkillDo create(String actorUserId, UserSkillCreateDto request);

    /**
     * 修改公共 Skill 并保留已有资源。
     *
     * @param actorUserId 操作用户 ID
     * @param name        Skill 名称
     * @param request     Skill 配置
     * @return 已修改 Skill
     */
    SkillDo update(String actorUserId, String name, UserSkillUpdateDto request);

    /**
     * 删除公共 Skill。
     *
     * @param actorUserId 操作用户 ID
     * @param name        Skill 名称
     * @return 是否删除成功
     */
    boolean delete(String actorUserId, String name);
}
