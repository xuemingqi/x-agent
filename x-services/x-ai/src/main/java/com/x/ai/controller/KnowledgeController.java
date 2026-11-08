package com.x.ai.controller;

import com.x.ai.common.domain.KnowledgeDocumentDo;
import com.x.ai.common.domain.KnowledgeDownloadDo;
import com.x.ai.service.KnowledgeService;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "KnowledgeController", description = "用户知识库管理")
@RequestMapping("/knowledge/documents")
@RestController
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @Operation(summary = "查询当前用户知识库文档")
    @Parameters(@Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true))
    @GetMapping
    public BaseResult<List<KnowledgeDocumentDo>> list() {
        return ResultUtil.buildResultSuccess(knowledgeService.list());
    }

    @Operation(summary = "上传当前用户知识库文档")
    @Parameters(@Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResult<List<KnowledgeDocumentDo>> upload(@RequestPart("files") List<MultipartFile> files) {
        return ResultUtil.buildResultSuccess(knowledgeService.upload(files));
    }

    @Operation(summary = "下载当前用户知识库文档")
    @Parameters(@Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true))
    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam String path) {
        KnowledgeDownloadDo download = knowledgeService.download(path);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.getFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(download.getContentType()))
                .body(download.getResource());
    }

    @Operation(summary = "删除当前用户知识库文档")
    @Parameters(@Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true))
    @DeleteMapping
    public BaseResult<Boolean> delete(@RequestParam String path) {
        return ResultUtil.buildResultSuccess(knowledgeService.delete(path));
    }
}
