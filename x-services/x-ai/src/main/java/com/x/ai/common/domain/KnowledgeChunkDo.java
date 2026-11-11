package com.x.ai.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 知识文档切片。
 */
@Data
@Accessors(chain = true)
@Schema(name = "KnowledgeChunkDo", description = "知识文档切片")
public class KnowledgeChunkDo {

    /**
     * 切片序号，从 1 开始。
     */
    @Schema(name = "index", description = "文档内的切片序号，从 1 开始", minimum = "1")
    private int index;

    /**
     * 切片文本。
     */
    @Schema(name = "content", description = "切片原始文本")
    private String content;

    /**
     * 切片 Unicode 字符数。
     */
    @Schema(name = "characterCount", description = "切片 Unicode 字符数", minimum = "0")
    private int characterCount;
}
