package com.x.ai.controller;

import com.x.ai.common.domain.KnowledgeLinkDo;
import com.x.ai.common.dto.KnowledgeLinkCreateDto;
import com.x.ai.common.dto.KnowledgeLinkUpdateDto;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import com.x.ai.service.KnowledgeLinkService;
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

@Tag(name = "KnowledgeLinkController", description = "在线知识库链接管理")
@RequestMapping("/knowledge/links")
@RestController
@RequiredArgsConstructor
public class KnowledgeLinkController {

    private final KnowledgeLinkService knowledgeLinkService;


    @Operation(summary = "查询当前用户的个人在线知识库链接")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @GetMapping
    public BaseResult<List<KnowledgeLinkDo>> list() {
        return ResultUtil.buildResultSuccess(knowledgeLinkService.list());
    }

    @Operation(summary = "创建当前用户的个人在线知识库链接")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping
    public BaseResult<KnowledgeLinkDo> create(@RequestBody @Valid KnowledgeLinkCreateDto request) {
        return ResultUtil.buildResultSuccess(knowledgeLinkService.create(request));
    }

    @Operation(summary = "修改当前用户的个人在线知识库链接")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PutMapping("/{id}")
    public BaseResult<KnowledgeLinkDo> update(@PathVariable Long id,
                                             @RequestBody @Valid KnowledgeLinkUpdateDto request) {
        return ResultUtil.buildResultSuccess(knowledgeLinkService.update(id, request));
    }

    @Operation(summary = "删除当前用户的个人在线知识库链接")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @DeleteMapping("/{id}")
    public BaseResult<Boolean> delete(@PathVariable Long id) {
        return ResultUtil.buildResultSuccess(knowledgeLinkService.delete(id));
    }
}
