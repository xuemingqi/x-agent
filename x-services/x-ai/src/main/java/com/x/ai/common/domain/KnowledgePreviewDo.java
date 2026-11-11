package com.x.ai.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 文档在线预览。
 */
@Data
@Accessors(chain = true)
@Schema(name = "KnowledgePreviewDo", description = "知识文档在线预览正文")
public class KnowledgePreviewDo {

    /**
     * 文档相对路径。
     */
    @Schema(name = "path", description = "文档在当前知识库中的相对路径")
    private String path;

    /**
     * 文档名称。
     */
    @Schema(name = "name", description = "文档名称")
    private String name;

    /**
     * 提取的纯文本正文，不执行原文 HTML 或脚本。
     */
    @Schema(name = "content", description = "解析提取的纯文本正文，不执行原文 HTML 或脚本")
    private String content;

    /**
     * 原文内容类型。
     */
    @Schema(name = "contentType", description = "原文 MIME 内容类型")
    private String contentType;
}
