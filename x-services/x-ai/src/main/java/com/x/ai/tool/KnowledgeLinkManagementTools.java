package com.x.ai.tool;

import com.x.ai.common.dto.KnowledgeLinkFolderToolDto;
import com.x.ai.common.dto.KnowledgeLinkToolDto;
import com.x.ai.common.model.ToolContext;
import com.x.ai.service.KnowledgeLinkManagementToolService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class KnowledgeLinkManagementTools {

    private final KnowledgeLinkManagementToolService knowledgeLinkManagementToolService;


    @Tool(name = "knowledge_link_list", readOnly = true, description = """
            查询当前用户的个人在线知识库链接完整配置，供新增、编辑、删除或移动时定位链接。
            返回 id、folderId、名称、简短介绍和 URL；所有 ID 均为字符串，folderId 为 "0" 表示根目录。
            不读取文档正文。名称有歧义时应先明确要操作的链接。
            """)
    public List<KnowledgeLinkToolDto> list(ToolContext context) {
        return knowledgeLinkManagementToolService.list(context);
    }

    @Tool(name = "knowledge_link_create", description = """
            为当前用户创建个人在线知识库链接，只保存名称、介绍和 URL，不抓取正文。
            name、summary 和 url 均必填且不能为空；URL 仅支持不携带内嵌凭证的 HTTP(S) 绝对链接。
            folderId 必须是个人文件夹 ID 字符串，省略或传 "0" 表示根目录；可先查询 knowledge_link_folder_list。
            """)
    public KnowledgeLinkToolDto create(@ToolParam(name = "name", description = "文档名称，最多 256 个字符") String name,
            @ToolParam(name = "summary", description = "文档简短介绍，最多 1000 个字符") String summary,
            @ToolParam(name = "url", description = "HTTP(S) 在线文档绝对 URL，最多 2048 个字符") String url,
            @ToolParam(name = "folderId", description = "个人文件夹 ID 字符串；省略或字符串 0 表示根目录", required = false) String folderId,
            ToolContext context) {
        return knowledgeLinkManagementToolService.create(context, name, summary, url, folderId);
    }

    @Tool(name = "knowledge_link_update", description = """
            部分编辑当前用户的个人在线知识库链接，id 必须来自 knowledge_link_list 且为字符串。
            name、summary、url、folderId 省略或 null 均保持原值，至少提供一个待修改字段；文本字段不能清空。
            folderId 为字符串 "0" 时移入根目录，其他值必须是本人的文件夹 ID 字符串。
            """)
    public KnowledgeLinkToolDto update(@ToolParam(name = "id", description = "个人链接 ID 字符串") String id,
            @ToolParam(name = "name", description = "新文档名称，最多 256 个字符；省略保留", required = false) String name,
            @ToolParam(name = "summary", description = "新简短介绍，最多 1000 个字符；省略保留", required = false) String summary,
            @ToolParam(name = "url", description = "新 HTTP(S) URL，最多 2048 个字符；省略保留", required = false) String url,
            @ToolParam(name = "folderId", description = "目标个人文件夹 ID 字符串；省略保留，字符串 0 表示根目录", required = false) String folderId,
            ToolContext context) {
        return knowledgeLinkManagementToolService.update(context, id, name, summary, url, folderId);
    }

    @Tool(name = "knowledge_link_delete", description = """
            删除用户明确指定的个人在线知识库链接记录，不删除链接指向的原始文档。
            应先使用 knowledge_link_list 定位，id 必须是个人链接 ID 字符串，不能操作其他用户的链接。
            """)
    public boolean delete(@ToolParam(name = "id", description = "用户要删除的个人链接 ID 字符串") String id,
                          ToolContext context) {
        return knowledgeLinkManagementToolService.delete(context, id);
    }

    @Tool(name = "knowledge_link_move", description = """
            将当前用户的个人在线知识库链接移入指定个人文件夹，保留名称、介绍和 URL。
            id 和 folderId 都必须是字符串；folderId 传 "0" 表示根目录。
            先用 knowledge_link_list 和 knowledge_link_folder_list 定位，不允许跨用户操作。
            """)
    public KnowledgeLinkToolDto move(@ToolParam(name = "id", description = "待移动的个人链接 ID 字符串") String id,
            @ToolParam(name = "folderId", description = "目标个人文件夹 ID 字符串；字符串 0 表示根目录") String folderId,
            ToolContext context) {
        return knowledgeLinkManagementToolService.move(context, id, folderId);
    }

    @Tool(name = "knowledge_link_folder_list", readOnly = true, description = """
            查询当前用户的个人在线知识库全部文件夹，返回平铺的 id、parentId 和 name。
            所有 ID 均为字符串；parentId 为 "0" 表示根目录，根目录本身不作为文件夹返回。
            用于解析创建位置、重命名、删除和移动目标；同名文件夹需结合父子关系定位。
            """)
    public List<KnowledgeLinkFolderToolDto> listFolders(ToolContext context) {
        return knowledgeLinkManagementToolService.listFolders(context);
    }

    @Tool(name = "knowledge_link_folder_create", description = """
            为当前用户创建个人在线知识库文件夹，name 为必填名称，同一父目录中不能重名。
            parentId 必须是本人的父文件夹 ID 字符串；省略或传字符串 "0" 表示根目录。
            """)
    public KnowledgeLinkFolderToolDto createFolder(@ToolParam(name = "name", description = "文件夹名称，最多 256 个字符") String name,
            @ToolParam(name = "parentId", description = "父文件夹 ID 字符串；省略或字符串 0 表示根目录", required = false) String parentId,
            ToolContext context) {
        return knowledgeLinkManagementToolService.createFolder(context, name, parentId);
    }

    @Tool(name = "knowledge_link_folder_update", description = """
            重命名当前用户的个人在线知识库文件夹；不改变父目录，同一父目录中不能重名。
            id 必须是 knowledge_link_folder_list 返回的文件夹 ID 字符串，根目录不可编辑。
            修改父目录请使用 knowledge_link_folder_move。
            """)
    public KnowledgeLinkFolderToolDto updateFolder(@ToolParam(name = "id", description = "个人文件夹 ID 字符串") String id,
            @ToolParam(name = "name", description = "新名称，不能为空且最多 256 个字符") String name,
            ToolContext context) {
        return knowledgeLinkManagementToolService.updateFolder(context, id, name);
    }

    @Tool(name = "knowledge_link_folder_delete", description = """
            删除用户明确指定的个人在线知识库空文件夹，根目录不能删除。
            不级联删除；有文档或子文件夹时会失败，应由用户决定移出或删除哪些内容后再操作。
            """)
    public boolean deleteFolder(@ToolParam(name = "id", description = "用户要删除的个人文件夹 ID 字符串") String id,
                                ToolContext context) {
        return knowledgeLinkManagementToolService.deleteFolder(context, id);
    }

    @Tool(name = "knowledge_link_folder_move", description = """
            移动当前用户的个人在线知识库文件夹，子文件夹和链接随父目录保留，文件夹名称不变。
            id 和 parentId 均为字符串，parentId 为 "0" 表示根目录；不可移动根目录。
            不允许移入自身、后代或其他用户目录；目标目录不能存在同名文件夹。
            """)
    public KnowledgeLinkFolderToolDto moveFolder(@ToolParam(name = "id", description = "待移动的个人文件夹 ID 字符串") String id,
            @ToolParam(name = "parentId", description = "目标父文件夹 ID 字符串；字符串 0 表示根目录") String parentId,
            ToolContext context) {
        return knowledgeLinkManagementToolService.moveFolder(context, id, parentId);
    }
}
