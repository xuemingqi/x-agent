package com.x.ai.controller;

import com.x.ai.common.domain.SkillDo;
import com.x.ai.common.domain.SkillShareDo;
import com.x.ai.common.dto.ResourceEnabledUpdateDto;
import com.x.ai.common.dto.SkillShareCreateDto;
import com.x.ai.common.dto.UserSkillCreateDto;
import com.x.ai.common.dto.UserSkillUpdateDto;
import com.x.ai.common.enums.SkillArchiveFormat;
import com.x.ai.common.enums.SkillScope;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import com.x.ai.service.SkillService;
import com.x.ai.service.SkillShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "SkillController", description = "Skill 管理")
@RequestMapping("/skills")
@RestController
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    private final SkillShareService skillShareService;


    @Operation(summary = "修改当前用户的扩展启用状态")
    @PutMapping("/{scope}/{name}/enabled")
    public BaseResult<Boolean> setEnabled(@PathVariable int scope, @PathVariable String name,
                                          @RequestBody @Valid ResourceEnabledUpdateDto request) {
        return ResultUtil.buildResultSuccess(skillService.setEnabled(SkillScope.fromCode(scope), name, request.getEnabled()));
    }

    @Operation(summary = "查询当前用户可见的 Skill")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @GetMapping
    public BaseResult<List<SkillDo>> list(@RequestParam(required = false) Integer scope) {
        return ResultUtil.buildResultSuccess(skillService.list(scope == null ? null : SkillScope.fromCode(scope)));
    }

    @Operation(summary = "查询 Skill 详情")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @GetMapping("/{scope}/{name}")
    public BaseResult<SkillDo> get(@PathVariable int scope, @PathVariable String name) {
        return ResultUtil.buildResultSuccess(skillService.get(SkillScope.fromCode(scope), name));
    }

    @Operation(summary = "创建当前用户 Skill")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping
    public BaseResult<SkillDo> create(@RequestBody @Valid UserSkillCreateDto request) {
        return ResultUtil.buildResultSuccess(skillService.create(request));
    }

    @Operation(summary = "使用用户默认模型导入个人 Skill 压缩包")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResult<SkillDo> importArchive(@RequestPart("file") MultipartFile file) {
        return ResultUtil.buildResultSuccess(skillService.importArchive(file));
    }

    @Operation(summary = "原样导出当前用户可见的 Skill 压缩包")
    @GetMapping("/{scope}/{name}/export")
    public ResponseEntity<byte[]> exportArchive(@PathVariable int scope, @PathVariable String name,
                                               @RequestParam(defaultValue = "zip") String format) {
        SkillArchiveFormat archiveFormat = SkillArchiveFormat.fromExtension(format);
        byte[] archive = skillService.exportArchive(SkillScope.fromCode(scope), name, archiveFormat);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(archiveFormat.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name + "." + archiveFormat.getExtension(), StandardCharsets.UTF_8).build().toString())
                .contentLength(archive.length).body(archive);
    }

    @Operation(summary = "修改当前用户 Skill")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PutMapping("/{name}")
    public BaseResult<SkillDo> update(@PathVariable String name, @RequestBody @Valid UserSkillUpdateDto request) {
        return ResultUtil.buildResultSuccess(skillService.update(name, request));
    }

    @Operation(summary = "删除当前用户 Skill")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @DeleteMapping("/{name}")
    public BaseResult<Boolean> delete(@PathVariable String name) {
        return ResultUtil.buildResultSuccess(skillService.delete(name));
    }

    @Operation(summary = "将当前用户的个人 Skill 分享给指定邮箱用户")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping("/{name}/shares")
    public BaseResult<SkillShareDo> share(@PathVariable String name, @RequestBody @Valid SkillShareCreateDto request) {
        return ResultUtil.buildResultSuccess(skillShareService.share(name, request));
    }

    @Operation(summary = "查询当前用户待处理的 Skill 分享邀请")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @GetMapping("/shares/incoming")
    public BaseResult<List<SkillShareDo>> listPendingShares() {
        return ResultUtil.buildResultSuccess(skillShareService.listPending());
    }

    @Operation(summary = "接受 Skill 分享邀请")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping("/shares/{shareId}/accept")
    public BaseResult<Boolean> acceptShare(@PathVariable Long shareId) {
        return ResultUtil.buildResultSuccess(skillShareService.accept(shareId));
    }

    @Operation(summary = "拒绝 Skill 分享邀请")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @PostMapping("/shares/{shareId}/reject")
    public BaseResult<Boolean> rejectShare(@PathVariable Long shareId) {
        return ResultUtil.buildResultSuccess(skillShareService.reject(shareId));
    }
}
