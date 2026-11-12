package com.x.ai.controller;

import com.x.ai.common.domain.KnowledgeBaseDo;
import com.x.ai.common.dto.KnowledgeBaseDto;
import com.x.ai.service.KnowledgeBaseService;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
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

@Tag(name = "KnowledgeBaseController", description = "个人知识库管理")
@RequestMapping("/knowledge/bases")
@RestController
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @Operation(summary = "列出当前用户知识库")
    @GetMapping
    public BaseResult<List<KnowledgeBaseDo>> list() {
        return ResultUtil.buildResultSuccess(knowledgeBaseService.list());
    }

    @Operation(summary = "新建知识库")
    @PostMapping
    public BaseResult<KnowledgeBaseDo> create(@RequestBody @Valid KnowledgeBaseDto request) {
        return ResultUtil.buildResultSuccess(knowledgeBaseService.create(request));
    }

    @Operation(summary = "编辑知识库")
    @PutMapping("/{id}")
    public BaseResult<KnowledgeBaseDo> update(@PathVariable String id, @RequestBody @Valid KnowledgeBaseDto request) {
        return ResultUtil.buildResultSuccess(knowledgeBaseService.update(id, request));
    }

    @Operation(summary = "删除知识库及全部文档")
    @DeleteMapping("/{id}")
    public BaseResult<Boolean> delete(@PathVariable String id) {
        return ResultUtil.buildResultSuccess(knowledgeBaseService.delete(id));
    }
}
