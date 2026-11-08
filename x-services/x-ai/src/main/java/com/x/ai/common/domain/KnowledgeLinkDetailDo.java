package com.x.ai.common.domain;

import lombok.Data;
import lombok.experimental.Accessors;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@Accessors(chain = true)
public class KnowledgeLinkDetailDo {

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
     * 在线文档链接。
     */
    private String url;
}
