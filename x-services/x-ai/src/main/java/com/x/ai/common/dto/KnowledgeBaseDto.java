package com.x.ai.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 知识库创建和编辑输入，所有者由认证上下文决定。
 */
@Data
@Accessors(chain = true)
public class KnowledgeBaseDto {

    /**
     * 知识库名称。
     */
    @NotBlank
    @Size(max = 100)
    private String name;

    /**
     * 知识库内容说明，省略时保存为空字符串。
     */
    @Size(max = 500)
    private String description;
}
