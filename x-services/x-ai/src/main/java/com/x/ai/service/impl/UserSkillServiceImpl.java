package com.x.ai.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.x.ai.common.constants.AgentConstant;
import com.x.ai.common.constants.SkillArchiveConstant;
import com.x.ai.common.dto.UserSkillCreateDto;
import com.x.ai.common.enums.SkillScope;
import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.db.entity.AgentUserSkill;
import com.x.ai.db.entity.AgentUserSkillResource;
import com.x.ai.db.service.AgentUserSkillIService;
import com.x.ai.db.service.AgentUserSkillResourceIService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.ai.service.UserSkillService;
import com.x.ai.skill.SkillArchiveCodec;
import com.x.ai.skill.SkillResourceCodec;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.util.MarkdownSkillParser;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class UserSkillServiceImpl implements UserSkillService {

    private final AgentUserSkillIService agentUserSkillIService;

    private final AgentUserSkillResourceIService agentUserSkillResourceIService;

    private final Snowflake snowflake;

    private final Validator validator;


    @Override
    public List<AgentSkill> list(String userId) {
        return loadSkills(agentUserSkillIService.lambdaQuery().eq(AgentUserSkill::getUserId, userId)
                .orderByAsc(AgentUserSkill::getName).list());
    }

    @Override
    public List<AgentSkill> listEnabled(String userId, ResourceEnablement enablement) {
        String prefix = ResourceEnablement.skillKey(SkillScope.USER, "");
        List<String> disabledNames = enablement.getDisabledKeys().stream()
                .filter(key -> key.startsWith(prefix)).map(key -> key.substring(prefix.length())).toList();
        return loadSkills(agentUserSkillIService.lambdaQuery().eq(AgentUserSkill::getUserId, userId)
                .notIn(!disabledNames.isEmpty(), AgentUserSkill::getName, disabledNames)
                .orderByAsc(AgentUserSkill::getName).list());
    }

    @Override
    public AgentSkill get(String userId, String name) {
        AgentUserSkill skill = findUserSkill(userId, name);
        return skill == null ? null : loadSkills(List.of(skill)).getFirst();
    }

    @Override
    @Transactional
    public AgentSkill save(String userId, AgentSkill skill, boolean overwrite) {
        Map<String, byte[]> resourceBytes = validateSkillPackage(skill);
        AgentUserSkill current = findUserSkill(userId, skill.getName());
        if (current != null && !overwrite) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_CONFLICT);
        }
        AgentUserSkill entity = new AgentUserSkill()
                .setId(current == null ? snowflake.nextId() : current.getId()).setUserId(userId)
                .setName(skill.getName()).setDescription(skill.getDescription()).setSkillContent(skill.getSkillContent())
                .setMetadata(new HashMap<>(skill.getMetadata()));
        if (!agentUserSkillIService.saveOrUpdate(entity)) {
            throw new XException(ResponseCodeEnum.AGENT_WORKSPACE_STORAGE_ERROR);
        }
        replaceResources(entity.getId(), resourceBytes);
        return skill.toBuilder().source(AgentConstant.USER_SKILL_SOURCE).build();
    }

    @Override
    @Transactional
    public boolean delete(String userId, String name) {
        AgentUserSkill skill = findUserSkill(userId, name);
        if (skill == null) {
            return false;
        }
        if (!agentUserSkillIService.lambdaUpdate().eq(AgentUserSkill::getUserId, userId).eq(AgentUserSkill::getId, skill.getId()).remove()) {
            throw new XException(ResponseCodeEnum.AGENT_WORKSPACE_STORAGE_ERROR);
        }
        return true;
    }

    /**
     * 在保存 Skill 的同一事务中整体替换附件，失败时正文与附件一起回滚。
     */
    private void replaceResources(Long skillId, Map<String, byte[]> resourceBytes) {
        agentUserSkillResourceIService.lambdaUpdate().eq(AgentUserSkillResource::getSkillId, skillId).remove();
        List<AgentUserSkillResource> files = resourceBytes.entrySet().stream()
                .map(file -> new AgentUserSkillResource().setId(snowflake.nextId()).setSkillId(skillId)
                        .setResourcePath(file.getKey()).setResourceContent(file.getValue()))
                .toList();
        if (!files.isEmpty() && !agentUserSkillResourceIService.saveBatch(files)) {
            throw new XException(ResponseCodeEnum.AGENT_WORKSPACE_STORAGE_ERROR);
        }
    }

    /**
     * 所有单条操作先限定所有者，不能通过名称或 ID 跨用户读取和修改。
     */
    private AgentUserSkill findUserSkill(String userId, String name) {
        return agentUserSkillIService.lambdaQuery().eq(AgentUserSkill::getUserId, userId).eq(AgentUserSkill::getName, name).one();
    }

    /**
     * 批量装配资源，避免每个 Skill 单独访问数据库。
     */
    private List<AgentSkill> loadSkills(List<AgentUserSkill> definitions) {
        if (definitions.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<String, String>> files = agentUserSkillResourceIService.lambdaQuery()
                .in(AgentUserSkillResource::getSkillId, definitions.stream().map(AgentUserSkill::getId).toList())
                .list().stream().collect(Collectors.groupingBy(AgentUserSkillResource::getSkillId,
                        Collectors.toMap(AgentUserSkillResource::getResourcePath,
                                resource -> SkillResourceCodec.encode(resource.getResourceContent()))));
        return definitions.stream().map(skill -> AgentSkill.builder()
                .metadata(skill.getMetadata()).name(skill.getName()).description(skill.getDescription())
                .skillContent(skill.getSkillContent()).resources(files.getOrDefault(skill.getId(), Map.of()))
                .source(AgentConstant.USER_SKILL_SOURCE).build()).toList();
    }

    /**
     * 页面与模型工具共用包校验；文件预算、路径及正文约束保持一致。
     */
    private Map<String, byte[]> validateSkillPackage(AgentSkill skill) {
        UserSkillCreateDto definition = new UserSkillCreateDto().setName(skill.getName())
                .setDescription(skill.getDescription()).setContent(skill.getSkillContent());
        if (!validator.validate(definition).isEmpty() || skill.getResources().size() >= SkillArchiveConstant.MAX_ENTRIES) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_INVALID);
        }
        Map<String, byte[]> decoded = skill.getResources().entrySet().stream()
                .collect(Collectors.toMap(file -> validateResourcePath(file.getKey(), skill.getResources().keySet()),
                        file -> SkillResourceCodec.decode(file.getValue())));
        long definitionBytes = MarkdownSkillParser.generate(skill.getMetadata(), skill.getSkillContent())
                .getBytes(StandardCharsets.UTF_8).length;
        long resourceBytes = decoded.values().stream().mapToLong(content -> content.length).sum();
        if (definitionBytes + resourceBytes > SkillArchiveConstant.MAX_EXPANDED_BYTES) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_ARCHIVE_INVALID);
        }
        return decoded;
    }

    /**
     * 资源路径必须已规范化，不得占用正文路径或与其他资源的父目录冲突。
     */
    private String validateResourcePath(String path, Set<String> resourcePaths) {
        String validated = SkillArchiveCodec.validatePath(path);
        boolean parentIsFile = Stream.iterate(Path.of(validated).getParent(), Objects::nonNull, Path::getParent)
                .map(Path::toString).anyMatch(resourcePaths::contains);
        if (!validated.equals(path) || validated.equals(SkillArchiveConstant.SKILL_FILE) || parentIsFile) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_INVALID);
        }
        return validated;
    }
}
