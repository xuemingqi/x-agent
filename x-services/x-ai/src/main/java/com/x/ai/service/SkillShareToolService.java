package com.x.ai.service;

import com.x.ai.common.dto.SkillShareToolDto;
import com.x.ai.common.model.ToolContext;

import java.util.List;

public interface SkillShareToolService {

    /**
     * 以可信工具上下文的用户身份分享个人 Skill，并转换为工具输出。
     *
     * @param context 平台注入的运行上下文
     * @param skillName 个人 Skill 名称
     * @param recipientMobile 用户指定的接收人手机号
     * @return ID 为字符串、状态为整数的邀请
     */
    SkillShareToolDto share(ToolContext context, String skillName, String recipientMobile);

    /**
     * 查询当前运行用户待处理的邀请，并转换为工具输出。
     *
     * @param context 平台注入的运行上下文
     * @return 待处理邀请列表
     */
    List<SkillShareToolDto> listPending(ToolContext context);

    /**
     * 解析字符串邀请 ID，以当前运行用户身份接受邀请。
     *
     * @param context 平台注入的运行上下文
     * @param shareId 正整数格式的邀请 ID 字符串
     * @return 是否接受成功
     */
    boolean accept(ToolContext context, String shareId);

    /**
     * 解析字符串邀请 ID，以当前运行用户身份拒绝邀请。
     *
     * @param context 平台注入的运行上下文
     * @param shareId 正整数格式的邀请 ID 字符串
     * @return 是否拒绝成功
     */
    boolean reject(ToolContext context, String shareId);
}
