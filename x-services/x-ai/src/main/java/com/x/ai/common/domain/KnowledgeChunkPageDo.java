package com.x.ai.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 切片分页结果。
 */
@Data
@Accessors(chain = true)
@Schema(name = "KnowledgeChunkPageDo", description = "知识文档切片分页结果")
public class KnowledgeChunkPageDo {

    /**
     * 当前页切片。
     */
    @Schema(name = "items", description = "当前页切片，未匹配时为空列表")
    private java.util.List<KnowledgeChunkDo> items;

    /**
     * 切片总数。
     */
    @Schema(name = "total", description = "文档切片总数", minimum = "0")
    private long total;

    /**
     * 页码，从 1 开始。
     */
    @Schema(name = "page", description = "当前页码，从 1 开始", minimum = "1")
    private int page;

    /**
     * 每页条数。
     */
    @Schema(name = "size", description = "每页切片条数", minimum = "1", maximum = "100")
    private int size;
}
