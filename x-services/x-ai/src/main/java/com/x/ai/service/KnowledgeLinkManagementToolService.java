package com.x.ai.service;

import com.x.ai.common.dto.KnowledgeLinkFolderToolDto;
import com.x.ai.common.dto.KnowledgeLinkToolDto;
import com.x.ai.common.model.ToolContext;

import java.util.List;

/**
 * 个人在线知识库的对话管理适配；可信身份、工具参数和输出格式不进入通用业务服务。
 */
public interface KnowledgeLinkManagementToolService {

    /**
     * 查询运行时用户的个人链接完整配置，所有 ID 以字符串返回。
     */
    List<KnowledgeLinkToolDto> list(ToolContext context);

    /**
     * 创建个人链接；folderId 省略或字符串 "0" 表示根目录。
     */
    KnowledgeLinkToolDto create(ToolContext context, String name, String summary, String url, String folderId);

    /**
     * 部分编辑个人链接，null 字段保留原值；至少提供一个字段，显式空文本无效。
     */
    KnowledgeLinkToolDto update(ToolContext context, String id, String name, String summary, String url, String folderId);

    /**
     * 删除明确指定的个人链接记录，id 必须是正整数格式的字符串。
     */
    boolean delete(ToolContext context, String id);

    /**
     * 将个人链接移入指定目录，folderId 必填且字符串 "0" 表示根目录。
     */
    KnowledgeLinkToolDto move(ToolContext context, String id, String folderId);

    /**
     * 查询个人文件夹平铺目录，id 和 parentId 均以字符串返回。
     */
    List<KnowledgeLinkFolderToolDto> listFolders(ToolContext context);

    /**
     * 创建个人文件夹；parentId 省略或字符串 "0" 表示根目录。
     */
    KnowledgeLinkFolderToolDto createFolder(ToolContext context, String name, String parentId);

    /**
     * 重命名个人文件夹，不改变目录关系。
     */
    KnowledgeLinkFolderToolDto updateFolder(ToolContext context, String id, String name);

    /**
     * 删除个人空文件夹，不允许删除根目录。
     */
    boolean deleteFolder(ToolContext context, String id);

    /**
     * 移动个人文件夹及其内容；parentId 必填且字符串 "0" 表示根目录。
     */
    KnowledgeLinkFolderToolDto moveFolder(ToolContext context, String id, String parentId);
}
