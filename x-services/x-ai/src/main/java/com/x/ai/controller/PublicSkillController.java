package com.x.ai.controller;

import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.ai.common.domain.SkillDo;
import com.x.ai.common.dto.UserSkillCreateDto;
import com.x.ai.common.dto.UserSkillUpdateDto;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import com.x.ai.service.PublicSkillService;
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

@Tag(name = "PublicSkillController", description = "管理员公共 Skill 管理")
@RequestMapping("/administration/skills")
@RestController
@RequiredArgsConstructor
public class PublicSkillController {

    private final PublicSkillService publicSkillService;

    @Operation(summary = "查询公共 Skill")
    @GetMapping
    public BaseResult<List<SkillDo>> list() {
        return ResultUtil.buildResultSuccess(publicSkillService.list(currentUserId()));
    }

    @Operation(summary = "查询公共 Skill 详情")
    @GetMapping("/{name}")
    public BaseResult<SkillDo> get(@PathVariable String name) {
        return ResultUtil.buildResultSuccess(publicSkillService.get(currentUserId(), name));
    }

    @Operation(summary = "创建公共 Skill")
    @PostMapping
    public BaseResult<SkillDo> create(@RequestBody @Valid UserSkillCreateDto request) {
        return ResultUtil.buildResultSuccess(publicSkillService.create(currentUserId(), request));
    }

    @Operation(summary = "修改公共 Skill")
    @PutMapping("/{name}")
    public BaseResult<SkillDo> update(@PathVariable String name, @RequestBody @Valid UserSkillUpdateDto request) {
        return ResultUtil.buildResultSuccess(publicSkillService.update(currentUserId(), name, request));
    }

    @Operation(summary = "删除公共 Skill")
    @DeleteMapping("/{name}")
    public BaseResult<Boolean> delete(@PathVariable String name) {
        return ResultUtil.buildResultSuccess(publicSkillService.delete(currentUserId(), name));
    }

    /**
     * 从认证边界读取当前可信用户 ID。
     */
    private String currentUserId() {
        return UserThreadLocalUtil.getUserId();
    }
}
