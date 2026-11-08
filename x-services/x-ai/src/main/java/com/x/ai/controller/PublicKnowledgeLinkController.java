package com.x.ai.controller;

import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.ai.common.domain.KnowledgeLinkDo;
import com.x.ai.common.domain.KnowledgeLinkFolderDo;
import com.x.ai.common.dto.KnowledgeLinkCreateDto;
import com.x.ai.common.dto.KnowledgeLinkFolderCreateDto;
import com.x.ai.common.dto.KnowledgeLinkFolderUpdateDto;
import com.x.ai.common.dto.KnowledgeLinkUpdateDto;
import com.x.ai.common.model.KnowledgeLinkParam;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import com.x.ai.service.PublicKnowledgeLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "PublicKnowledgeLinkController", description = "管理员公共在线知识库管理")
@RequestMapping("/administration/knowledge")
@RestController
@RequiredArgsConstructor
public class PublicKnowledgeLinkController {

    private final PublicKnowledgeLinkService publicKnowledgeLinkService;


    @Operation(summary = "查询公共在线知识库链接")
    @GetMapping("/links")
    public BaseResult<List<KnowledgeLinkDo>> listLinks() {
        return ResultUtil.buildResultSuccess(publicKnowledgeLinkService.listLinks(currentUserId()));
    }

    @Operation(summary = "创建公共在线知识库链接")
    @PostMapping("/links")
    public BaseResult<KnowledgeLinkDo> createLink(@RequestBody @Valid KnowledgeLinkCreateDto request) {
        return ResultUtil.buildResultSuccess(publicKnowledgeLinkService.createLink(currentUserId(), toParam(request)));
    }

    @Operation(summary = "修改公共在线知识库链接")
    @PutMapping("/links/{id}")
    public BaseResult<KnowledgeLinkDo> updateLink(@PathVariable Long id, @RequestBody @Valid KnowledgeLinkUpdateDto request) {
        return ResultUtil.buildResultSuccess(publicKnowledgeLinkService.updateLink(currentUserId(), id, toParam(request)));
    }

    @Operation(summary = "删除公共在线知识库链接")
    @DeleteMapping("/links/{id}")
    public BaseResult<Boolean> deleteLink(@PathVariable Long id) {
        return ResultUtil.buildResultSuccess(publicKnowledgeLinkService.deleteLink(currentUserId(), id));
    }

    @Operation(summary = "查询公共在线知识库文件夹")
    @GetMapping("/link-folders")
    public BaseResult<List<KnowledgeLinkFolderDo>> listFolders() {
        return ResultUtil.buildResultSuccess(publicKnowledgeLinkService.listFolders(currentUserId()));
    }

    @Operation(summary = "创建公共在线知识库文件夹")
    @PostMapping("/link-folders")
    public BaseResult<KnowledgeLinkFolderDo> createFolder(@RequestBody @Valid KnowledgeLinkFolderCreateDto request) {
        return ResultUtil.buildResultSuccess(publicKnowledgeLinkService.createFolder(
                currentUserId(), request.getParentId(), request.getName()));
    }

    @Operation(summary = "重命名公共在线知识库文件夹")
    @PutMapping("/link-folders/{id}")
    public BaseResult<KnowledgeLinkFolderDo> renameFolder(@PathVariable Long id, @RequestBody @Valid KnowledgeLinkFolderUpdateDto request) {
        return ResultUtil.buildResultSuccess(publicKnowledgeLinkService.renameFolder(
                currentUserId(), id, request.getName()));
    }

    @Operation(summary = "删除公共在线知识库空文件夹")
    @DeleteMapping("/link-folders/{id}")
    public BaseResult<Boolean> deleteFolder(@PathVariable Long id) {
        return ResultUtil.buildResultSuccess(publicKnowledgeLinkService.deleteFolder(currentUserId(), id));
    }

    /**
     * 转换公共链接创建参数。
     */
    private KnowledgeLinkParam toParam(KnowledgeLinkCreateDto request) {
        return new KnowledgeLinkParam()
                .setFolderId(request.getFolderId())
                .setName(request.getName())
                .setSummary(request.getSummary())
                .setUrl(request.getUrl());
    }

    /**
     * 转换公共链接更新参数。
     */
    private KnowledgeLinkParam toParam(KnowledgeLinkUpdateDto request) {
        return new KnowledgeLinkParam()
                .setFolderId(request.getFolderId())
                .setName(request.getName())
                .setSummary(request.getSummary())
                .setUrl(request.getUrl());
    }

    /**
     * 从认证边界读取当前可信用户 ID。
     */
    private String currentUserId() {
        return UserThreadLocalUtil.getUserId();
    }
}
