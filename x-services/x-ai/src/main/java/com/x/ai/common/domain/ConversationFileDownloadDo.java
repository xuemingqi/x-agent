package com.x.ai.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.core.io.Resource;

@Data
@Accessors(chain = true)
@Schema(name = "ConversationFileDownloadDo", description = "会话文件下载内容")
public class ConversationFileDownloadDo {

    @Schema(name = "fileName", description = "下载文件名")
    private String fileName;

    @Schema(name = "contentType", description = "文件媒体类型")
    private String contentType;

    @Schema(name = "sizeBytes", description = "文件字节数")
    private long sizeBytes;

    @Schema(hidden = true)
    private Resource resource;
}
