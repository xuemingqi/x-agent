package com.x.ai.db.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("agent_knowledge_link")
public class AgentKnowledgeLink {

    /**
     * 在线知识库链接 ID。
     */
    @TableId("id")
    private Long id;

    /**
     * 所属文件夹 ID，0 表示当前用户的根目录。
     */
    @TableField("folder_id")
    private Long folderId;

    /**
     * 文档名称。
     */
    @TableField("name")
    private String name;

    /**
     * 文档简短介绍。
     */
    @TableField("summary")
    private String summary;

    /**
     * 在线文档链接。
     */
    @TableField("url")
    private String url;

    /**
     * 规范化 URL 的 SHA-256 摘要。
     */
    @TableField("url_hash")
    private String urlHash;

    /**
     * 链接所属用户 ID。
     */
    @TableField("owner_user_id")
    private String ownerUserId;

    /**
     * 是否启用。
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * 创建时间。
     */
    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
