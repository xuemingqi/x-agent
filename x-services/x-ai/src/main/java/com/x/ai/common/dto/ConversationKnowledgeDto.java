package com.x.ai.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class ConversationKnowledgeDto {

    /**
     * 额外关联的个人知识库 ID；公共知识库自动参与检索，不需要显式选择。
     */
    @NotNull
    @Size(max = 50)
    private List<@NotBlank @Size(max = 160) String> knowledgeBaseIds = List.of();
}
