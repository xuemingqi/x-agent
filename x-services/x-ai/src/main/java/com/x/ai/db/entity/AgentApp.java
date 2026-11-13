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
@TableName("agent_app")
public class AgentApp {

    /**
     * 个人应用 ID。
     */
    @TableId("id")
    private String id;

    /**
     * 应用所属用户 ID。
     */
    @TableField("owner_user_id")
    private String ownerUserId;

    /**
     * 当前发布版本的展示名称。
     */
    @TableField("name")
    private String name;

    /**
     * 当前发布版本的能力介绍。
     */
    @TableField("description")
    private String description;

    /**
     * 当前发布的不可变版本 ID。
     */
    @TableField("current_version")
    private String currentVersion;

    /**
     * 是否已进入删除流程；对象存储故障时保留引用以便重试清理。
     */
    @TableField("deleted")
    private Boolean deleted;

    /**
     * 创建时间，由数据库维护。
     */
    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    /**
     * 更新时间，由数据库维护。
     */
    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
