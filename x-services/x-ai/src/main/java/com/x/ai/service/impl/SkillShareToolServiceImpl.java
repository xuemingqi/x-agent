package com.x.ai.service.impl;

import com.x.ai.common.domain.SkillShareDo;
import com.x.ai.common.dto.SkillShareToolDto;
import com.x.ai.common.model.SkillShareCreateParam;
import com.x.ai.common.model.ToolContext;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.service.SkillShareManagementService;
import com.x.ai.service.SkillShareToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SkillShareToolServiceImpl implements SkillShareToolService {

    private final SkillShareManagementService skillShareManagementService;


    @Override
    public SkillShareToolDto share(ToolContext context, String skillName, String recipientMobile) {
        SkillShareCreateParam param = new SkillShareCreateParam().setRecipientMobile(recipientMobile);
        return toToolDto(skillShareManagementService.share(requireUserId(context), skillName, param));
    }

    @Override
    public List<SkillShareToolDto> listPending(ToolContext context) {
        return skillShareManagementService.listPending(requireUserId(context)).stream().map(this::toToolDto).toList();
    }

    @Override
    public boolean accept(ToolContext context, String shareId) {
        return skillShareManagementService.accept(requireUserId(context), StringUtil.parseLongId(shareId));
    }

    @Override
    public boolean reject(ToolContext context, String shareId) {
        return skillShareManagementService.reject(requireUserId(context), StringUtil.parseLongId(shareId));
    }

    /**
     * 运行时上下文由平台注入，不接受模型指定发送人或接收人身份。
     */
    private String requireUserId(ToolContext context) {
        if (context == null || StringUtil.isBlank(context.getUserId())) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        return context.getUserId();
    }

    /**
     * 使用基础 JSON 类型输出，避免 AgentScope 与 Web 序列化器不同导致枚举或 ID 变形。
     */
    private SkillShareToolDto toToolDto(SkillShareDo share) {
        return new SkillShareToolDto()
                .setId(share.getId())
                .setSkillName(share.getSkillName())
                .setDescription(share.getDescription())
                .setSenderMobile(share.getSenderMobile())
                .setSenderDisplayName(share.getSenderDisplayName())
                .setStatus(share.getStatus().getCode())
                .setResourcePaths(share.getResourcePaths())
                .setCreateTime(share.getCreateTime() == null ? null : share.getCreateTime().toString());
    }
}
