package com.x.ai.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@Schema(name = "KnowledgeDocumentDo", description = "用户知识库文档")
public class KnowledgeDocumentDo {

    @Schema(name = "path", description = "文档在当前用户知识库中的相对路径")
    private String path;

    @Schema(name = "name", description = "文档名称")
    private String name;

    @Schema(name = "size", description = "文档字节数")
    private long size;

    @Schema(name = "contentType", description = "文档内容类型")
    private String contentType;

    @Schema(name = "updateTime", description = "文档更新时间")
    private LocalDateTime updateTime;
}
