package com.x.ai.common.model;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class KnowledgeLinkParam {

    /**
     * 所属文件夹 ID；创建时未指定则使用根目录，更新时未指定则保留原目录。
     */
    private Long folderId;

    /**
     * 文档名称。
     */
    private String name;

    /**
     * 文档简短介绍。
     */
    private String summary;

    /**
     * 在线文档链接。
     */
    private String url;
}
