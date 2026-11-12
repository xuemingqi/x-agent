package com.x.ai.common.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 当前用户的独立文档知识库。
 */
@Data
@Accessors(chain = true)
public class KnowledgeBaseDo {

    /**
     * 知识库 ID，文档和会话通过此值关联。
     */
    private String id;

    /**
     * 用户设置的知识库名称。
     */
    private String name;

    /**
     * 知识库内容说明。
     */
    private String description;

    /**
     * 是否为当前用户所有对话自动检索的公共知识库。
     */
    @Getter(onMethod_ = @JsonProperty("isDefault"))
    private boolean isDefault;
}
