package com.x.ai.common.model;

import com.x.ai.common.enums.SkillManagementAction;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 个人 Skill 管理的业务参数，操作编码由工具入口解析，所有者由可信上下文提供。
 */
@Data
@Accessors(chain = true)
public class SkillManagementParam {

    /**
     * 已解析的 Skill 管理动作。
     */
    private SkillManagementAction action;

    /**
     * 当前用户的个人 Skill 名称。
     */
    private String name;

    /**
     * 创建或编辑时提交的完整 SKILL.md，包含 frontmatter 与正文。
     */
    private String content;

    /**
     * Skill 包内的资源相对路径；补丁操作为空时修改 SKILL.md。
     */
    private String filePath;

    /**
     * 写入资源文件的 UTF-8 文本内容。
     */
    private String fileContent;

    /**
     * 精确补丁中需要匹配的原文。
     */
    private String oldString;

    /**
     * 精确补丁的替换文本，空字符串表示删除匹配内容。
     */
    private String newString;

    /**
     * 是否替换全部匹配；为 false 时要求原文只匹配一次。
     */
    private boolean replaceAll;
}
