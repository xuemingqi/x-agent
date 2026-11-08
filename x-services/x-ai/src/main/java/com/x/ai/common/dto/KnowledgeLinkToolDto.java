package com.x.ai.common.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 对话管理使用的个人链接配置；ID 使用字符串，不依赖 Web JSON 序列化器。
 */
@Data
@Accessors(chain = true)
public class KnowledgeLinkToolDto {

    private String id;

    private String folderId;

    private String name;

    private String summary;

    private String url;
}
