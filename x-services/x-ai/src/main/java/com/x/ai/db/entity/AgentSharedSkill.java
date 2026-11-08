package com.x.ai.db.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("agent_shared_skill")
public class AgentSharedSkill {

    /**
     * 共享 Skill 快照 ID。
     */
    @TableId("id")
    private Long id;

    /**
     * 快照创建者用户 ID。
     */
    @TableField("owner_user_id")
    private String ownerUserId;

    /**
     * Skill 名称。
     */
    @TableField("name")
    private String name;

    /**
     * Skill 使用场景描述。
     */
    @TableField("description")
    private String description;

    /**
     * 发布时的 SKILL.md 指令正文快照。
     */
    @TableField("skill_content")
    private String skillContent;

    /**
     * 创建时间。
     */
    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;
}
