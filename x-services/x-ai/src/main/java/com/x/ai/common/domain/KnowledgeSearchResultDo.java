package com.x.ai.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 语义检索结果。
 */
@Data
@Accessors(chain = true)
@Schema(name = "KnowledgeSearchResultDo", description = "知识库语义检索命中切片")
public class KnowledgeSearchResultDo {

    /**
     * 来源文档相对路径。
     */
    @Schema(name = "path", description = "来源文档在所属知识库中的相对路径")
    private String path;

    /**
     * 来源文档名称。
     */
    @Schema(name = "name", description = "来源文档名称")
    private String name;

    /**
     * 来源切片序号，从 1 开始。
     */
    @Schema(name = "chunkIndex", description = "来源切片序号，从 1 开始", minimum = "1")
    private int chunkIndex;

    /**
     * 命中切片正文。
     */
    @Schema(name = "content", description = "命中切片的原始正文")
    private String content;

    /**
     * 余弦相似度，值越大越相关。
     */
    @Schema(name = "score", description = "余弦相似度，值越大越相关", minimum = "-1", maximum = "1")
    private double score;

    /**
     * 来源范围，user 或 public。
     */
    @Schema(name = "scope", description = "来源知识库范围", allowableValues = {"user", "public"})
    private String scope;
}
