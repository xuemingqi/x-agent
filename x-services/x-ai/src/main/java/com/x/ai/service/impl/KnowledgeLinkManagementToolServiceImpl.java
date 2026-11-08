package com.x.ai.service.impl;

import com.x.ai.common.constants.KnowledgeLinkConstant;
import com.x.ai.common.domain.KnowledgeLinkDo;
import com.x.ai.common.domain.KnowledgeLinkFolderDo;
import com.x.ai.common.dto.KnowledgeLinkFolderToolDto;
import com.x.ai.common.dto.KnowledgeLinkToolDto;
import com.x.ai.common.model.KnowledgeLinkParam;
import com.x.ai.common.model.ToolContext;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.service.KnowledgeLinkFolderManagementService;
import com.x.ai.service.KnowledgeLinkManagementService;
import com.x.ai.service.KnowledgeLinkManagementToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeLinkManagementToolServiceImpl implements KnowledgeLinkManagementToolService {

    private final KnowledgeLinkManagementService knowledgeLinkManagementService;

    private final KnowledgeLinkFolderManagementService knowledgeLinkFolderManagementService;


    @Override
    public List<KnowledgeLinkToolDto> list(ToolContext context) {
        return knowledgeLinkManagementService.list(currentUserId(context)).stream().map(this::toLinkDto).toList();
    }

    @Override
    public KnowledgeLinkToolDto create(ToolContext context, String name, String summary, String url, String folderId) {
        String userId = currentUserId(context);
        KnowledgeLinkParam param = new KnowledgeLinkParam().setName(name).setSummary(summary).setUrl(url)
                .setFolderId(parseOptionalFolderId(folderId));
        return toLinkDto(knowledgeLinkManagementService.create(userId, param));
    }

    @Override
    public KnowledgeLinkToolDto update(ToolContext context, String id, String name, String summary, String url, String folderId) {
        String userId = currentUserId(context);
        KnowledgeLinkParam param = new KnowledgeLinkParam().setName(name).setSummary(summary).setUrl(url)
                .setFolderId(parseOptionalFolderId(folderId));
        return toLinkDto(knowledgeLinkManagementService.patch(userId, parseId(id), param));
    }

    @Override
    public boolean delete(ToolContext context, String id) {
        return knowledgeLinkManagementService.delete(currentUserId(context), parseId(id));
    }

    @Override
    public KnowledgeLinkToolDto move(ToolContext context, String id, String folderId) {
        return toLinkDto(knowledgeLinkManagementService.patch(currentUserId(context), parseId(id),
                new KnowledgeLinkParam().setFolderId(parseFolderId(folderId))));
    }

    @Override
    public List<KnowledgeLinkFolderToolDto> listFolders(ToolContext context) {
        return knowledgeLinkFolderManagementService.list(currentUserId(context)).stream().map(this::toFolderDto).toList();
    }

    @Override
    public KnowledgeLinkFolderToolDto createFolder(ToolContext context, String name, String parentId) {
        return toFolderDto(knowledgeLinkFolderManagementService.create(currentUserId(context), parseOptionalFolderId(parentId), name));
    }

    @Override
    public KnowledgeLinkFolderToolDto updateFolder(ToolContext context, String id, String name) {
        return toFolderDto(knowledgeLinkFolderManagementService.rename(currentUserId(context), parseId(id), name));
    }

    @Override
    public boolean deleteFolder(ToolContext context, String id) {
        return knowledgeLinkFolderManagementService.delete(currentUserId(context), parseId(id));
    }

    @Override
    public KnowledgeLinkFolderToolDto moveFolder(ToolContext context, String id, String parentId) {
        return toFolderDto(knowledgeLinkFolderManagementService.move(currentUserId(context), parseId(id), parseFolderId(parentId)));
    }

    /**
     * 只接受运行时注入的可信用户，不依赖 Web 请求线程身份。
     */
    private String currentUserId(ToolContext context) {
        if (context == null || StringUtil.isBlank(context.getUserId())) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        return context.getUserId();
    }

    /**
     * 拒绝科学计数法、符号、前导零及超范围输入，避免 ID 在模型边界发生变形。
     */
    private Long parseId(String id) {
        if (id == null || !id.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("ID 必须是正整数格式的字符串");
        }
        return StringUtil.parseLongId(id);
    }

    /**
     * 根目录仅在目录参数中允许，源资源 ID 始终必须为正整数。
     */
    private Long parseFolderId(String id) {
        return "0".equals(id) ? KnowledgeLinkConstant.ROOT_FOLDER_ID : parseId(id);
    }

    /**
     * 保留省略目录的语义；空串属于无效输入，不能静默移动到根目录。
     */
    private Long parseOptionalFolderId(String id) {
        return id == null ? null : parseFolderId(id);
    }

    /**
     * 工具只输出本人链接的管理字段，ID 类型与 AgentScope JSON 实现无关。
     */
    private KnowledgeLinkToolDto toLinkDto(KnowledgeLinkDo link) {
        return new KnowledgeLinkToolDto().setId(link.getId().toString()).setFolderId(link.getFolderId().toString())
                .setName(link.getName()).setSummary(link.getSummary()).setUrl(link.getUrl());
    }

    /**
     * 对话目录保留完整父子关系，根目录也始终输出为字符串 "0"。
     */
    private KnowledgeLinkFolderToolDto toFolderDto(KnowledgeLinkFolderDo folder) {
        return new KnowledgeLinkFolderToolDto().setId(folder.getId().toString()).setParentId(folder.getParentId().toString())
                .setName(folder.getName());
    }
}
