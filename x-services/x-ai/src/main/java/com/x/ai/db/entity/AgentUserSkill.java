package com.x.ai.db.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Accessors(chain = true)
@TableName(value = "agent_user_skill", autoResultMap = true)
public class AgentUserSkill {

    /**
     * 个人 Skill ID。
     */
    @TableId("id")
    private Long id;

    /**
     * 所属用户 ID，所有读写均限定此用户。
     */
    @TableField("user_id")
    private String userId;

    /**
     * 用户内唯一的 Skill 名称。
     */
    @TableField("name")
    private String name;

    /**
     * Skill 使用场景。
     */
    @TableField("description")
    private String description;

    /**
     * 不含 YAML frontmatter 的指令正文。
     */
    @TableField("skill_content")
    private String skillContent;

    /**
     * 完整 frontmatter，保留扩展字段。
     */
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 数据库维护的创建时间。
     */
    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    /**
     * 数据库维护的更新时间。
     */
    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
