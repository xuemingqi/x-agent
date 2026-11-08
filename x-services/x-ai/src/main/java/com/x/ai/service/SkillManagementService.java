package com.x.ai.service;

import com.x.ai.common.model.SkillManagementParam;
import com.x.ai.common.model.ToolContext;

public interface SkillManagementService {

    /**
     * 模型仅管理运行时上下文用户的已启用个人 Skill；所有修改保存到数据库。
     */
    boolean manage(ToolContext context, SkillManagementParam param);

    /**
     * 保存一个默认关闭的个人 Skill 建议，用户可在管理页面检查并启用。
     */
    String propose(ToolContext context, String content);
}
