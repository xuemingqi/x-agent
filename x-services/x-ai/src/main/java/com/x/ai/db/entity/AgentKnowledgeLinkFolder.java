package com.x.ai.db.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.x.ai.db.enums.AgentKnowledgeLinkScope;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("agent_knowledge_link_folder")
public class AgentKnowledgeLinkFolder {

    /**
     * 在线知识库文件夹 ID。
     */
    @TableId("id")
    private Long id;

    /**
     * 父文件夹 ID，0 表示当前作用域的根目录。
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 文件夹名称。
     */
    @TableField("name")
    private String name;

    /**
     * 文件夹作用域，与所属文档和父文件夹保持一致。
     */
    @TableField("scope")
    private AgentKnowledgeLinkScope scope;

    /**
     * 个人文件夹所属用户 ID，公共文件夹固定为空字符串。
     */
    @TableField("owner_user_id")
    private String ownerUserId;

    /**
     * 创建用户 ID。
     */
    @TableField("created_by")
    private String createdBy;

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
