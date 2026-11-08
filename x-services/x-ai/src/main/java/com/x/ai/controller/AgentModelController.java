package com.x.ai.controller;

import com.x.ai.service.AiModelService;
import com.x.api.ai.domain.AiModelDo;
import com.x.api.ai.dto.AiModelSaveDto;
import com.x.framework.common.response.BaseResult;
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

@Tag(name = "AgentModelController", description = "模型配置管理")
@RequestMapping("/models")
@RestController
@RequiredArgsConstructor
public class AgentModelController {

    private final AiModelService aiModelService;


    @Operation(summary = "查询当前用户可用模型")
    @GetMapping
    public BaseResult<List<AiModelDo>> list() {
        return aiModelService.models();
    }

    @Operation(summary = "新增当前用户模型配置")
    @PostMapping
    public BaseResult<AiModelDo> create(@RequestBody @Valid AiModelSaveDto request) {
        return aiModelService.create(request);
    }

    @Operation(summary = "更新当前用户模型配置")
    @PutMapping("/{id}")
    public BaseResult<AiModelDo> update(@PathVariable Long id, @RequestBody @Valid AiModelSaveDto request) {
        return aiModelService.update(id, request);
    }

    @Operation(summary = "删除当前用户模型配置")
    @DeleteMapping("/{id}")
    public BaseResult<Void> delete(@PathVariable Long id) {
        return aiModelService.delete(id);
    }
}
