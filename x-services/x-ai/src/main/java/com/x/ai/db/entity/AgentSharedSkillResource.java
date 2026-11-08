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
@TableName("agent_shared_skill_resource")
public class AgentSharedSkillResource {

    /**
     * 资源 ID。
     */
    @TableId("id")
    private Long id;

    /**
     * 所属共享 Skill 快照 ID。
     */
    @TableField("skill_id")
    private Long skillId;

    /**
     * Skill 目录内的资源相对路径。
     */
    @TableField("resource_path")
    private String resourcePath;

    /**
     * 资源文本内容。
     */
    @TableField("resource_content")
    private String resourceContent;

    /**
     * 创建时间。
     */
    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;
}
