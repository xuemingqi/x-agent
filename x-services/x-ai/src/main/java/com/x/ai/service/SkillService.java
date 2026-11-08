package com.x.ai.service;

import com.x.ai.common.domain.SkillDo;
import com.x.ai.common.dto.UserSkillCreateDto;
import com.x.ai.common.dto.UserSkillUpdateDto;
import com.x.ai.common.enums.SkillScope;
import com.x.ai.common.enums.SkillArchiveFormat;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SkillService {

    /**
     * 保存当前用户对指定可见资源的启用偏好，不修改公共资源及分享状态。
     */
    boolean setEnabled(SkillScope scope, String name, boolean enabled);


    /**
     * 查询当前用户可见的公共和用户 Skill。
     *
     * @param scope 可选作用域，为空时返回全部
     * @return Skill 列表
     */
    List<SkillDo> list(SkillScope scope);

    /**
     * 查询指定作用域的 Skill 详情。
     *
     * @param scope Skill 作用域
     * @param name  Skill 名称
     * @return Skill 详情
     */
    SkillDo get(SkillScope scope, String name);

    /**
     * 在数据库中创建当前用户的个人 Skill。
     *
     * @param request Skill 配置
     * @return 已创建 Skill
     */
    SkillDo create(UserSkillCreateDto request);

    /**
     * 修改当前用户的 Skill，并保留已有附属资源。
     *
     * @param name    Skill 名称
     * @param request 新的 Skill 配置
     * @return 修改后的 Skill
     */
    SkillDo update(String name, UserSkillUpdateDto request);

    /**
     * 删除当前用户的 Skill 及其附属资源。
     *
     * @param name Skill 名称
     * @return 是否删除成功
     */
    boolean delete(String name);

    /**
     * 使用当前用户默认模型导入压缩包中的个人 Skill，同名时拒绝覆盖。
     */
    SkillDo importArchive(MultipartFile file);

    /**
     * 导出当前用户可见的指定 Skill 及其全部资源。
     */
    byte[] exportArchive(SkillScope scope, String name, SkillArchiveFormat format);
}
