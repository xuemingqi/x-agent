package com.x.ai.service.impl;

import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.ai.common.constants.AgentConstant;
import com.x.ai.common.domain.SkillDo;
import com.x.ai.common.dto.UserSkillCreateDto;
import com.x.ai.common.dto.UserSkillUpdateDto;
import com.x.ai.common.enums.SkillArchiveFormat;
import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.converter.SkillConverter;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.ai.service.ResourceEnablementService;
import com.x.ai.service.SkillArchiveService;
import com.x.ai.service.SkillService;
import com.x.ai.service.UserSkillService;
import io.agentscope.core.skill.AgentSkill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private final UserSkillService userSkillService;

    private final SkillArchiveService skillArchiveService;

    private final ResourceEnablementService resourceEnablementService;


    @Override
    public List<SkillDo> list() {
        String userId = currentUserId();
        ResourceEnablement enablement = resourceEnablementService.get(userId);
        return userSkillService.list(userId).stream()
                .map(skill -> SkillConverter.toSkillDo(skill, enablement)).toList();
    }

    @Override
    public boolean setEnabled(String name, boolean enabled) {
        requireSkill(name);
        resourceEnablementService.setEnabled(currentUserId(), ResourceEnablement.skillKey(name), enabled);
        return enabled;
    }

    @Override
    public SkillDo get(String name) {
        return toSkillDo(requireSkill(name));
    }

    /**
     * 详情、编辑和导出共用所有者查询，不能通过同名 Skill 跨用户读取内容。
     */
    private AgentSkill requireSkill(String name) {
        AgentSkill skill = userSkillService.get(currentUserId(), name);
        if (skill == null) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_NOT_FOUND);
        }
        return skill;
    }

    @Override
    public SkillDo importArchive(MultipartFile file) {
        AgentSkill skill = skillArchiveService.read(file);
        return toSkillDo(userSkillService.save(currentUserId(), skill, false));
    }

    @Override
    public byte[] exportArchive(String name, SkillArchiveFormat format) {
        return skillArchiveService.write(requireSkill(name), format);
    }

    @Override
    public SkillDo create(UserSkillCreateDto request) {
        AgentSkill skill = buildSkill(request.getName(), request.getDescription(), request.getContent(), Map.of());
        return toSkillDo(userSkillService.save(currentUserId(), skill, false));
    }

    @Override
    public SkillDo update(String name, UserSkillUpdateDto request) {
        AgentSkill current = requireSkill(name);
        AgentSkill updated = current.toBuilder().description(request.getDescription().trim())
                .skillContent(request.getContent().trim()).build();
        return toSkillDo(userSkillService.save(currentUserId(), updated, true));
    }

    @Override
    public boolean delete(String name) {
        if (!userSkillService.delete(currentUserId(), name)) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_NOT_FOUND);
        }
        return true;
    }

    /**
     * 管理接口只从已认证上下文取得所有者身份。
     */
    private String currentUserId() {
        return UserThreadLocalUtil.getUserId();
    }

    /**
     * 构建符合 AgentScope SKILL.md 格式的 Skill，并保留附属文件内容。
     */
    private AgentSkill buildSkill(String name, String description, String content, Map<String, String> resources) {
        return AgentSkill.builder()
                .name(name)
                .description(description.trim())
                .skillContent(content.trim())
                .resources(resources)
                .source(AgentConstant.USER_SKILL_SOURCE)
                .build();
    }

    /**
     * 单条读写结果也使用当前用户的启用状态；列表转换则复用一次批量读取的快照。
     */
    private SkillDo toSkillDo(AgentSkill skill) {
        ResourceEnablement enablement = resourceEnablementService.get(UserThreadLocalUtil.getUserId());
        return SkillConverter.toSkillDo(skill, enablement);
    }
}
