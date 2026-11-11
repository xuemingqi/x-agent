package com.x.ai.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeProperties {

    /**
     * 单个原文文件的最大字节数。
     */
    @NotNull
    @Min(1)
    private Long maxUploadSizeBytes;

    /**
     * 单个切片的最大 Unicode 字符数。
     */
    @NotNull
    @Min(1)
    private Integer chunkSize;

    /**
     * 相邻切片重叠的 Unicode 字符数。
     */
    @NotNull
    @Min(0)
    private Integer chunkOverlap;

    /**
     * 单文档提取正文的最大 UTF-16 长度，超过时拒绝而非截断。
     */
    @NotNull
    @Min(1)
    private Integer maxContentCharacters;

    /**
     * 检索结果的最低余弦相似度。
     */
    @NotNull
    @DecimalMin("-1")
    @DecimalMax("1")
    private Double minScore;

    @AssertTrue(message = "切片重叠长度必须小于切片长度")
    public boolean isChunkOverlapValid() {
        return chunkOverlap == null || chunkSize == null || chunkOverlap < chunkSize;
    }
}
