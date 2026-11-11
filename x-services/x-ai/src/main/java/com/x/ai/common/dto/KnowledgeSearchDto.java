package com.x.ai.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 知识库语义检索输入，身份与作用域由认证入口确定。
 */
@Data
@Accessors(chain = true)
@Schema(name = "KnowledgeSearchDto", description = "当前知识库作用域内的语义检索请求")
public class KnowledgeSearchDto {

    /**
     * 用于匹配文档切片的自然语言问题。
     */
    @NotBlank
    @Size(max = 2000)
    @Schema(name = "query", description = "用于匹配文档切片的自然语言问题", maxLength = 2000)
    private String query;

    /**
     * 最多返回的切片数，默认五条。
     */
    @Min(1)
    @Max(20)
    @Schema(name = "limit", description = "最多返回的切片数", minimum = "1", maximum = "20", defaultValue = "5")
    private int limit = 5;
}
