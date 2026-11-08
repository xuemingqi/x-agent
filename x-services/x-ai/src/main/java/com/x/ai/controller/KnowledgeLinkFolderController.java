package com.x.ai.controller;

import com.x.ai.common.domain.KnowledgeLinkFolderDo;
import com.x.ai.common.dto.KnowledgeLinkFolderCreateDto;
import com.x.ai.common.dto.KnowledgeLinkFolderUpdateDto;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import com.x.ai.service.KnowledgeLinkFolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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

@Tag(name = "KnowledgeLinkFolderController", description = "在线知识库文件夹管理")
@RequestMapping("/knowledge/link-folders")
@RestController
@RequiredArgsConstructor
public class KnowledgeLinkFolderController {

    private final KnowledgeLinkFolderService knowledgeLinkFolderService;


    @Operation(summary = "查询当前用户的个人在线知识库文件夹")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @GetMapping
    public BaseResult<List<KnowledgeLinkFolderDo>> list() {
        return ResultUtil.buildResultSuccess(knowledgeLinkFolderService.list());
    }

    @Operation(summary = "创建当前用户的个人在线知识库文件夹")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping
    public BaseResult<KnowledgeLinkFolderDo> create(@RequestBody @Valid KnowledgeLinkFolderCreateDto request) {
        return ResultUtil.buildResultSuccess(knowledgeLinkFolderService.create(request));
    }

    @Operation(summary = "重命名当前用户的个人在线知识库文件夹")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PutMapping("/{id}")
    public BaseResult<KnowledgeLinkFolderDo> rename(@PathVariable Long id, @RequestBody @Valid KnowledgeLinkFolderUpdateDto request) {
        return ResultUtil.buildResultSuccess(knowledgeLinkFolderService.rename(id, request));
    }

    @Operation(summary = "删除当前用户的个人在线知识库空文件夹")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @DeleteMapping("/{id}")
    public BaseResult<Boolean> delete(@PathVariable Long id) {
        return ResultUtil.buildResultSuccess(knowledgeLinkFolderService.delete(id));
    }
}
