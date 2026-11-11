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

    /**
     * 索引状态：INDEXING、READY、FAILED。
     */
    @Schema(name = "status", description = "文档索引状态", allowableValues = {"INDEXING", "READY", "FAILED"})
    private String status;

    /**
     * 已成功发布的文档切片总数。
     */
    @Schema(name = "chunkCount", description = "已成功发布的切片总数", minimum = "0")
    private int chunkCount;

    /**
     * 索引使用的向量模型，尚未成功索引时为空字符串。
     */
    @Schema(name = "embeddingModel", description = "索引使用的向量模型，尚未成功索引时为空字符串")
    private String embeddingModel;

    /**
     * 可向用户展示的索引失败原因，成功时为空。
     */
    @Schema(name = "errorMessage", description = "索引失败原因，成功时为空")
    private String errorMessage;
}
