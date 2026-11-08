package com.x.ai.common.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 对话管理使用的个人目录配置；parentId 为字符串 "0" 时属于根目录。
 */
@Data
@Accessors(chain = true)
public class KnowledgeLinkFolderToolDto {

    private String id;

    private String parentId;

    private String name;
}
