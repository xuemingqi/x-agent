package com.x.ai.controller;

import com.x.ai.common.domain.ConversationFileDo;
import com.x.ai.common.domain.ConversationFileDownloadDo;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import com.x.framework.common.annotation.LogIgnore;
import com.x.ai.service.ConversationFileService;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "ConversationFileController", description = "会话文件管理")

@RequestMapping("/conversations/{sessionId}/files")
@RestController
@RequiredArgsConstructor
public class ConversationFileController {

    private final ConversationFileService conversationFileService;


    @Operation(summary = "上传本次会话使用的文档")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @LogIgnore
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResult<List<ConversationFileDo>> upload(@PathVariable String sessionId, @RequestPart("files") List<MultipartFile> files) {
        return ResultUtil.buildResultSuccess(conversationFileService.upload(sessionId, files));
    }

    @Operation(summary = "下载会话文件")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @LogIgnore
    @GetMapping("/{fileId}/content")
    public ResponseEntity<Resource> download(@PathVariable String sessionId, @PathVariable Long fileId) {
        ConversationFileDownloadDo download = conversationFileService.download(sessionId, fileId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.getFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(download.getContentType()))
                .contentLength(download.getSizeBytes())
                .body(download.getResource());
    }

    @Operation(summary = "删除尚未发送的会话附件")
    @Parameters({
            @Parameter(name = CommonConstant.TOKEN, description = "token", in = ParameterIn.HEADER, required = true)
    })
    @DeleteMapping("/{fileId}")
    public BaseResult<Boolean> deletePending(@PathVariable String sessionId, @PathVariable Long fileId) {
        return ResultUtil.buildResultSuccess(conversationFileService.deletePending(sessionId, fileId));
    }
}
