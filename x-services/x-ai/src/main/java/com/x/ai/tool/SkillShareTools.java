package com.x.ai.tool;

import com.x.ai.common.dto.SkillShareToolDto;
import com.x.ai.common.model.ToolContext;
import com.x.ai.service.SkillShareToolService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SkillShareTools {

    private final SkillShareToolService skillShareToolService;


    @Tool(name = "skill_share", description = """
            将当前用户自己的个人 Skill 以不可变快照分享给用户明确指定手机号的已注册平台用户。
            仅在用户明确要求分享时调用；不能分享公共 Skill 或收到的共享 Skill，不能给自己分享。
            接收人需要接受邀请后才能使用；返回邀请不表示对方已经接受。
            同一接收人尚待处理或已接受的同一 Skill 不能重复分享；被拒绝后可重新分享最新快照。
            返回 id 为邀请 ID 字符串；status 为整数：1 待处理，2 已接受，3 已拒绝。
            """)
    public SkillShareToolDto share(@ToolParam(name = "skillName", description = "当前用户要分享的个人 Skill 名称") String skillName,
                                   @ToolParam(name = "recipientMobile", description = "用户明确指定的接收人平台手机号") String recipientMobile,
                                   ToolContext context) {
        return skillShareToolService.share(context, skillName, recipientMobile);
    }

    @Tool(name = "skill_share_pending_list", readOnly = true, description = """
            查询发给当前用户且尚未处理的 Skill 分享邀请，返回 Skill 名称、描述、分享人和资源路径。
            id 是邀请 ID 字符串，可用于 skill_share_accept 和 skill_share_reject；status=1 表示待处理。
            用户未指定邀请或同名邀请存在歧义时，应先让用户选择，不能默认接受或拒绝。
            """)
    public List<SkillShareToolDto> listPending(ToolContext context) {
        return skillShareToolService.listPending(context);
    }

    @Tool(name = "skill_share_accept", description = """
            接受当前用户明确指定的待处理 Skill 分享邀请，使共享 Skill 进入可用仓库。
            shareId 必须使用 skill_share_pending_list 返回的邀请 ID 字符串，不能传 Skill 名称或数字。
            仅邀请接收人可接受；已接受或已拒绝的邀请不能再次处理，同名共享 Skill 冲突会报错。
            接受后可从后续 Skill 目录读取中查询，当前轮次已生成的系统提示词不会自动更新。
            """)
    public boolean accept(@ToolParam(name = "shareId", description = "分享邀请 ID，必须是正整数格式的字符串") String shareId,
                          ToolContext context) {
        return skillShareToolService.accept(context, shareId);
    }

    @Tool(name = "skill_share_reject", description = """
            拒绝当前用户明确指定的待处理 Skill 分享邀请。
            shareId 必须使用 skill_share_pending_list 返回的邀请 ID 字符串，不能传 Skill 名称或数字。
            仅邀请接收人可拒绝；已接受或已拒绝的邀请不能再次处理。
            """)
    public boolean reject(@ToolParam(name = "shareId", description = "分享邀请 ID，必须是正整数格式的字符串") String shareId,
                          ToolContext context) {
        return skillShareToolService.reject(context, shareId);
    }
}
