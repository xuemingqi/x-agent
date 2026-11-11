package com.x.ai.service.impl;

import com.x.ai.common.constants.SkillArchiveConstant;
import com.x.ai.common.enums.SkillManagementAction;
import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.common.model.SkillManagementParam;
import com.x.ai.common.model.ToolContext;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.service.ResourceEnablementService;
import com.x.ai.service.SkillManagementService;
import com.x.ai.service.UserSkillService;
import com.x.ai.skill.SkillArchiveCodec;
import com.x.ai.skill.SkillResourceCodec;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.util.MarkdownSkillParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SkillManagementServiceImpl implements SkillManagementService {

    private static final Set<String> RESOURCE_DIRECTORIES = Set.of("references", "templates", "scripts", "assets");

    private final UserSkillService userSkillService;

    private final ResourceEnablementService resourceEnablementService;


    @Override
    public boolean manage(ToolContext context, SkillManagementParam param) {
        String userId = requireUserId(context);
        if (param == null || StringUtil.isBlank(param.getName())
                || !resourceEnablementService.get(userId).isSkillEnabled(param.getName())) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_NOT_FOUND);
        }
        SkillManagementAction action = param.getAction();
        if (action == null) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_INVALID);
        }
        if (action == SkillManagementAction.CREATE) {
            userSkillService.save(userId, parseDefinition(param.getName(), param.getContent(), Map.of()), false);
            return true;
        }
        AgentSkill current = userSkillService.get(userId, param.getName());
        if (current == null) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_NOT_FOUND);
        }
        if (action == SkillManagementAction.DELETE) {
            return userSkillService.delete(userId, param.getName());
        }
        AgentSkill updated = switch (action) {
            case EDIT -> parseDefinition(param.getName(), param.getContent(), current.getResources());
            case WRITE_FILE -> writeResource(current, param.getFilePath(), param.getFileContent());
            case REMOVE_FILE -> current.toBuilder().removeResource(validateResourcePath(param.getFilePath())).build();
            case PATCH -> applyPatch(current, param);
            default -> throw new XException(ResponseCodeEnum.AGENT_SKILL_INVALID);
        };
        userSkillService.save(userId, updated, true);
        return true;
    }

    @Override
    @Transactional
    public String propose(ToolContext context, String content) {
        String userId = requireUserId(context);
        AgentSkill proposed = parseDefinition(null, content, Map.of());
        userSkillService.save(userId, proposed, false);
        resourceEnablementService.setEnabled(userId, ResourceEnablement.skillKey(proposed.getName()), false);
        return proposed.getName();
    }

    /**
     * 身份由平台工具上下文注入，模型不能指定所有者。
     */
    private String requireUserId(ToolContext context) {
        if (context == null || StringUtil.isBlank(context.getUserId())) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        return context.getUserId();
    }

    /**
     * 正文与附件一起存储，不通过 SDK 的可写目录仓库旁路加载过滤。
     */
    private AgentSkill parseDefinition(String expectedName, String content, Map<String, String> resources) {
        if (StringUtil.isBlank(content) || content.length() > SkillArchiveConstant.MAX_INSTRUCTION_CHARACTERS) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_INVALID);
        }
        var parsed = MarkdownSkillParser.parse(content);
        if (expectedName != null && !expectedName.equals(parsed.getMetadata().get("name"))) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_INVALID);
        }
        return AgentSkill.builder().metadata(parsed.getMetadata()).skillContent(parsed.getContent())
                .resources(resources).build();
    }

    /**
     * 仅允许修改 Skill 包内的资源目录，SKILL.md 统一通过正文操作修改。
     */
    private String validateResourcePath(String path) {
        String validated = SkillArchiveCodec.validatePath(path);
        int separator = validated.indexOf('/');
        if (!validated.equals(path) || separator < 0 || !RESOURCE_DIRECTORIES.contains(validated.substring(0, separator))) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_INVALID);
        }
        return validated;
    }

    /**
     * 资源写入遵循与上传相同的原始字节编码。
     */
    private AgentSkill writeResource(AgentSkill current, String path, String content) {
        if (content == null) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_INVALID);
        }
        return current.toBuilder().addResource(validateResourcePath(path),
                SkillResourceCodec.encode(content.getBytes(StandardCharsets.UTF_8))).build();
    }

    /**
     * 精确替换正文或 UTF-8 资源，非唯一匹配必须显式指定全部替换。
     */
    private AgentSkill applyPatch(AgentSkill current, SkillManagementParam param) {
        boolean instructions = StringUtil.isBlank(param.getFilePath())
                || SkillArchiveConstant.SKILL_FILE.equals(param.getFilePath());
        String original = instructions ? MarkdownSkillParser.generate(current.getMetadata(), current.getSkillContent())
                : readResource(current, validateResourcePath(param.getFilePath()));
        String oldText = param.getOldString();
        if (oldText == null || oldText.isEmpty() || param.getNewString() == null || !original.contains(oldText)
                || (!param.isReplaceAll() && original.indexOf(oldText) != original.lastIndexOf(oldText))) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_INVALID);
        }
        String updated = original.replace(oldText, param.getNewString());
        return instructions ? parseDefinition(current.getName(), updated, current.getResources())
                : writeResource(current, param.getFilePath(), updated);
    }

    /**
     * 二进制附件不能被文本补丁隐式损坏。
     */
    private String readResource(AgentSkill skill, String path) {
        String content = skill.getResource(path);
        if (content == null) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_NOT_FOUND);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(SkillResourceCodec.decode(content))).toString();
        } catch (CharacterCodingException error) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_INVALID);
        }
    }
}
