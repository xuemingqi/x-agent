package com.x.ai.db.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@TableName("agent_user_skill_resource")
public class AgentUserSkillResource {

    /**
     * 资源 ID。
     */
    @TableId("id")
    private Long id;

    /**
     * 所属个人 Skill ID。
     */
    @TableField("skill_id")
    private Long skillId;

    /**
     * Skill 内的相对路径，不包含 SKILL.md。
     */
    @TableField("resource_path")
    private String resourcePath;

    /**
     * 资源原始字节，文本和二进制文件均无损保存。
     */
    @TableField("resource_content")
    private byte[] resourceContent;
}
