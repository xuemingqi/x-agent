package com.x.ai.tool;

import com.x.ai.common.enums.SkillManagementAction;
import com.x.ai.common.model.SkillManagementParam;
import com.x.ai.common.model.ToolContext;
import com.x.ai.service.SkillManagementService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SkillManagementTools {

    private final SkillManagementService skillManagementService;


    @Tool(name = "skill_manage", description = """
            管理当前用户的个人 Skill，保存到数据库，后续对话生效。
            action 支持 create/edit/patch/write_file/remove_file/delete。
            create/edit 的 content 必须是完整 SKILL.md（含 name、description frontmatter）。
            patch 使用 old_string/new_string 精确替换，file_path 为空时修改 SKILL.md。
            write_file/remove_file 的路径必须位于 references、templates、scripts 或 assets 内。
            不能访问或修改已禁用的 Skill；公共与共享 Skill 不可通过此工具修改。
            """)
    public boolean manage(@ToolParam(name = "action", description = "操作名称") String action,
                          @ToolParam(name = "name", description = "个人 Skill 名称") String name,
                          @ToolParam(name = "content", required = false, description = "完整 SKILL.md") String content,
                          @ToolParam(name = "file_path", required = false, description = "包内相对路径") String filePath,
                          @ToolParam(name = "file_content", required = false, description = "写入的资源文本") String fileContent,
                          @ToolParam(name = "old_string", required = false, description = "待替换的原文") String oldString,
                          @ToolParam(name = "new_string", required = false, description = "替换后的文本") String newString,
                          @ToolParam(name = "replace_all", required = false, description = "是否替换全部匹配") Boolean replaceAll,
                          ToolContext context) {
        return skillManagementService.manage(context, new SkillManagementParam()
                .setAction(SkillManagementAction.fromCode(action)).setName(name).setContent(content)
                .setFilePath(filePath).setFileContent(fileContent).setOldString(oldString).setNewString(newString)
                .setReplaceAll(Boolean.TRUE.equals(replaceAll)));
    }

    @Tool(name = "propose_skill", description = """
            将完整 SKILL.md 保存为当前用户的个人 Skill 建议，默认关闭。
            返回 Skill 名称；用户可在 Skill 管理页面检查并启用，不能声称已可用。
            """)
    public String propose(@ToolParam(name = "content", description = "含 name、description frontmatter 的完整 SKILL.md") String content,
                          ToolContext context) {
        return skillManagementService.propose(context, content);
    }
}
