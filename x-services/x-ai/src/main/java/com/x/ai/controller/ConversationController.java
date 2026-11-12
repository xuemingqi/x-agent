package com.x.ai.controller;

import com.x.ai.common.domain.ConversationDo;
import com.x.ai.common.dto.ConversationKnowledgeDto;
import jakarta.validation.Valid;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import com.x.ai.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "ConversationController", description = "对话管理")
@RequestMapping("/conversations")
@RestController
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @Operation(summary = "新建对话")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping
    public BaseResult<ConversationDo> create(@RequestBody(required = false) @Valid ConversationKnowledgeDto request) {
        return ResultUtil.buildResultSuccess(conversationService.create(
                request == null ? List.of() : request.getKnowledgeBaseIds()));
    }

    @Operation(summary = "更新对话关联知识库")
    @PutMapping("/{sessionId}/knowledge-bases")
    public BaseResult<ConversationDo> updateKnowledgeBases(@PathVariable String sessionId,
                                                          @RequestBody @Valid ConversationKnowledgeDto request) {
        return ResultUtil.buildResultSuccess(conversationService.updateKnowledgeBases(
                sessionId, request.getKnowledgeBaseIds()));
    }

    @Operation(summary = "查询对话列表")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @GetMapping
    public BaseResult<List<ConversationDo>> list() {
        return ResultUtil.buildResultSuccess(conversationService.list());
    }

    @Operation(summary = "查询对话内容")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @GetMapping("/{sessionId}")
    public BaseResult<ConversationDo> get(@PathVariable String sessionId) {
        return ResultUtil.buildResultSuccess(conversationService.get(sessionId));
    }

    @Operation(summary = "删除对话")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @DeleteMapping("/{sessionId}")
    public BaseResult<Boolean> delete(@PathVariable String sessionId) {
        return ResultUtil.buildResultSuccess(conversationService.delete(sessionId));
    }
}
