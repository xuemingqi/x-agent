package com.x.ai.tool;

import com.x.ai.common.domain.KnowledgeDocumentDo;
import com.x.ai.common.domain.KnowledgeLinkDo;
import com.x.ai.common.domain.KnowledgeLinkFolderDo;
import com.x.ai.common.domain.SkillDo;
import com.x.ai.common.dto.UserSkillCreateDto;
import com.x.ai.common.dto.UserSkillUpdateDto;
import com.x.ai.common.model.KnowledgeLinkParam;
import com.x.ai.common.model.ToolContext;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.mcp.api.domain.McpServerDo;
import com.x.ai.mcp.api.dto.PublicMcpServerCreateDto;
import com.x.ai.mcp.api.dto.PublicMcpServerUpdateDto;
import com.x.ai.service.PublicKnowledgeLinkService;
import com.x.ai.service.PublicKnowledgeService;
import com.x.ai.service.PublicMcpServerService;
import com.x.ai.service.PublicSkillService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PublicAdministrationTools {

    private final PublicKnowledgeService publicKnowledgeService;

    private final PublicKnowledgeLinkService publicKnowledgeLinkService;

    private final PublicSkillService publicSkillService;

    private final PublicMcpServerService publicMcpServerService;


    @Tool(name = "public_admin_knowledge_file_list", readOnly = true,
            description = "查询公共知识库文件；仅管理员可用。文件正文可从公共知识库挂载目录读取")
    public List<KnowledgeDocumentDo> listKnowledgeFiles(ToolContext context) {
        return publicKnowledgeService.list(context.getUserId());
    }

    @Tool(name = "public_admin_knowledge_file_write",
            description = "创建或覆盖 UTF-8 公共知识文本文件；仅管理员可用，path 是公共知识库相对路径")
    public KnowledgeDocumentDo writeKnowledgeFile(
            @ToolParam(name = "path", description = "相对路径，例如 policies/rule.md") String path,
            @ToolParam(name = "content", description = "完整文本内容，最多 100000 字符") String content,
            ToolContext context) {
        return publicKnowledgeService.writeText(context.getUserId(), path, content);
    }

    @Tool(name = "public_admin_knowledge_file_delete",
            description = "删除公共知识库中的指定文件；仅管理员可用")
    public boolean deleteKnowledgeFile(
            @ToolParam(name = "path", description = "公共知识库相对路径") String path,
            ToolContext context) {
        return publicKnowledgeService.delete(context.getUserId(), path);
    }

    @Tool(name = "public_admin_knowledge_link_list", readOnly = true,
            description = "查询公共在线知识库链接；仅管理员可用")
    public List<KnowledgeLinkDo> listKnowledgeLinks(ToolContext context) {
        return publicKnowledgeLinkService.listLinks(context.getUserId());
    }

    @Tool(name = "public_admin_knowledge_link_create",
            description = "创建公共在线知识库链接；仅管理员可用，folderId 不传或传 0 表示根目录")
    public KnowledgeLinkDo createKnowledgeLink(
            @ToolParam(name = "request", description = "名称、介绍、URL 和可选文件夹 ID") KnowledgeLinkParam request,
            ToolContext context) {
        return publicKnowledgeLinkService.createLink(context.getUserId(), request);
    }

    @Tool(name = "public_admin_knowledge_link_update",
            description = "更新公共在线知识库链接的完整配置；仅管理员可用")
    public KnowledgeLinkDo updateKnowledgeLink(
            @ToolParam(name = "id", description = "公共在线知识链接 ID") String id,
            @ToolParam(name = "request", description = "新的完整链接配置") KnowledgeLinkParam request,
            ToolContext context) {
        return publicKnowledgeLinkService.updateLink(context.getUserId(), StringUtil.parseLongId(id), request);
    }

    @Tool(name = "public_admin_knowledge_link_delete",
            description = "删除公共在线知识库链接；仅管理员可用")
    public boolean deleteKnowledgeLink(
            @ToolParam(name = "id", description = "公共在线知识链接 ID") String id,
            ToolContext context) {
        return publicKnowledgeLinkService.deleteLink(context.getUserId(), StringUtil.parseLongId(id));
    }

    @Tool(name = "public_admin_knowledge_folder_list", readOnly = true,
            description = "查询公共在线知识库文件夹；仅管理员可用")
    public List<KnowledgeLinkFolderDo> listKnowledgeFolders(ToolContext context) {
        return publicKnowledgeLinkService.listFolders(context.getUserId());
    }

    @Tool(name = "public_admin_knowledge_folder_create",
            description = "创建公共在线知识库文件夹；仅管理员可用，parentId 不传或传 0 表示根目录")
    public KnowledgeLinkFolderDo createKnowledgeFolder(
            @ToolParam(name = "parentId", description = "父文件夹 ID") String parentId,
            @ToolParam(name = "name", description = "文件夹名称") String name,
            ToolContext context) {
        Long parsedParentId = StringUtil.isBlank(parentId) ? null : StringUtil.parseLongId(parentId);
        return publicKnowledgeLinkService.createFolder(context.getUserId(), parsedParentId, name);
    }

    @Tool(name = "public_admin_knowledge_folder_rename",
            description = "重命名公共在线知识库文件夹；仅管理员可用")
    public KnowledgeLinkFolderDo renameKnowledgeFolder(
            @ToolParam(name = "id", description = "文件夹 ID") String id,
            @ToolParam(name = "name", description = "新名称") String name,
            ToolContext context) {
        return publicKnowledgeLinkService.renameFolder(context.getUserId(), StringUtil.parseLongId(id), name);
    }

    @Tool(name = "public_admin_knowledge_folder_delete",
            description = "删除公共在线知识库空文件夹；仅管理员可用")
    public boolean deleteKnowledgeFolder(
            @ToolParam(name = "id", description = "文件夹 ID") String id,
            ToolContext context) {
        return publicKnowledgeLinkService.deleteFolder(context.getUserId(), StringUtil.parseLongId(id));
    }

    @Tool(name = "public_admin_skill_list", readOnly = true,
            description = "查询公共 Skill；仅管理员可用")
    public List<SkillDo> listSkills(ToolContext context) {
        return publicSkillService.list(context.getUserId());
    }

    @Tool(name = "public_admin_skill_get", readOnly = true,
            description = "查询公共 Skill 详情；仅管理员可用")
    public SkillDo getSkill(@ToolParam(name = "name", description = "Skill 名称") String name,
                            ToolContext context) {
        return publicSkillService.get(context.getUserId(), name);
    }

    @Tool(name = "public_admin_skill_create",
            description = "创建公共 Skill；仅管理员可用")
    public SkillDo createSkill(
            @ToolParam(name = "request", description = "Skill 名称、描述和指令正文") UserSkillCreateDto request,
            ToolContext context) {
        return publicSkillService.create(context.getUserId(), request);
    }

    @Tool(name = "public_admin_skill_update",
            description = "修改公共 Skill 并保留已有资源；仅管理员可用")
    public SkillDo updateSkill(
            @ToolParam(name = "name", description = "Skill 名称") String name,
            @ToolParam(name = "request", description = "新的描述和完整指令正文") UserSkillUpdateDto request,
            ToolContext context) {
        return publicSkillService.update(context.getUserId(), name, request);
    }

    @Tool(name = "public_admin_skill_delete", description = "删除公共 Skill；仅管理员可用")
    public boolean deleteSkill(@ToolParam(name = "name", description = "Skill 名称") String name,
                               ToolContext context) {
        return publicSkillService.delete(context.getUserId(), name);
    }

    @Tool(name = "public_admin_mcp_list", readOnly = true,
            description = "查询公共 MCP 的管理员配置；仅管理员可用")
    public List<McpServerDo> listMcps(ToolContext context) {
        return publicMcpServerService.list(context.getUserId());
    }

    @Tool(name = "public_admin_mcp_get", readOnly = true,
            description = "查询公共 MCP 管理详情；仅管理员可用，凭证只返回 key 不返回 value")
    public McpServerDo getMcp(@ToolParam(name = "id", description = "公共 MCP 配置 ID") String id,
                              ToolContext context) {
        return publicMcpServerService.get(context.getUserId(), StringUtil.parseLongId(id));
    }

    @Tool(name = "public_admin_mcp_create",
            description = "创建公共 MCP；仅管理员可用")
    public McpServerDo createMcp(
            @ToolParam(name = "request", description = "公共 MCP 完整配置") PublicMcpServerCreateDto request,
            ToolContext context) {
        return publicMcpServerService.create(context.getUserId(), request);
    }

    @Tool(name = "public_admin_mcp_update",
            description = "修改公共 MCP 的完整配置；仅管理员可用")
    public McpServerDo updateMcp(
            @ToolParam(name = "id", description = "公共 MCP 配置 ID") String id,
            @ToolParam(name = "request", description = "新的公共 MCP 完整配置") PublicMcpServerUpdateDto request,
            ToolContext context) {
        return publicMcpServerService.update(context.getUserId(), StringUtil.parseLongId(id), request);
    }

    @Tool(name = "public_admin_mcp_delete", description = "删除公共 MCP；仅管理员可用")
    public boolean deleteMcp(@ToolParam(name = "id", description = "公共 MCP 配置 ID") String id,
                             ToolContext context) {
        return publicMcpServerService.delete(context.getUserId(), StringUtil.parseLongId(id));
    }
}
