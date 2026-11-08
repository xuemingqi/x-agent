package com.x.ai.controller;

import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.ai.common.domain.KnowledgeDocumentDo;
import com.x.ai.common.domain.KnowledgeDownloadDo;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import com.x.ai.service.PublicKnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
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

@Tag(name = "PublicKnowledgeController", description = "管理员公共知识库管理")
@RequestMapping("/administration/knowledge/documents")
@RestController
@RequiredArgsConstructor
public class PublicKnowledgeController {

    private final PublicKnowledgeService publicKnowledgeService;


    @Operation(summary = "查询公共知识库文档")
    @GetMapping
    public BaseResult<List<KnowledgeDocumentDo>> list() {
        return ResultUtil.buildResultSuccess(publicKnowledgeService.list(currentUserId()));
    }

    @Operation(summary = "上传公共知识库文档")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResult<List<KnowledgeDocumentDo>> upload(@RequestPart("files") List<MultipartFile> files) {
        return ResultUtil.buildResultSuccess(publicKnowledgeService.upload(currentUserId(), files));
    }

    @Operation(summary = "下载公共知识库文档")
    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam String path) {
        KnowledgeDownloadDo download = publicKnowledgeService.download(currentUserId(), path);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.getFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(download.getContentType()))
                .body(download.getResource());
    }

    @Operation(summary = "删除公共知识库文档")
    @DeleteMapping
    public BaseResult<Boolean> delete(@RequestParam String path) {
        return ResultUtil.buildResultSuccess(publicKnowledgeService.delete(currentUserId(), path));
    }

    /**
     * 从认证边界读取当前可信用户 ID。
     */
    private String currentUserId() {
        return UserThreadLocalUtil.getUserId();
    }
}
