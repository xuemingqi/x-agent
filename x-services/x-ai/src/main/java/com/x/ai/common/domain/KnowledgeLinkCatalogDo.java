package com.x.ai.common.domain;

import lombok.Data;
import lombok.experimental.Accessors;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@Accessors(chain = true)
public class KnowledgeLinkCatalogDo {

    /**
     * 在线知识库链接 ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 文档名称。
     */
    private String name;

    /**
     * 文档简短介绍。
     */
    private String summary;
}
