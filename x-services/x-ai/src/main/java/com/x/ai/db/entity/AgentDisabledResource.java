package com.x.ai.db.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@TableName("agent_disabled_resource")
public class AgentDisabledResource {

    /**
     * 用户 ID 与资源键的 SHA-256 摘要，用于幂等写入。
     */
    @TableId(value = "id")
    private String id;

    /**
     * 关闭扩展的用户 ID。
     */
    @TableField("user_id")
    private String userId;

    /**
     * 资源键：SKILL:作用域:名称或 MCP:配置 ID。
     */
    @TableField("resource_key")
    private String resourceKey;
}
