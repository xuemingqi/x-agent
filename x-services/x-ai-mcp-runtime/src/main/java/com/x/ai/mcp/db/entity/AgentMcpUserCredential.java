package com.x.ai.mcp.db.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("agent_mcp_user_credential")
public class AgentMcpUserCredential {

    /**
     * 用户 MCP 凭证配置主键。
     */
    @TableId("id")
    private Long id;

    /**
     * 用户 ID。
     */
    @TableField("user_id")
    private String userId;

    /**
     * 多个公司内部公共 MCP 共享的凭证组。
     */
    @TableField("credential_group")
    private String credentialGroup;

    /**
     * 当前用户的 MCP 凭证值。
     */
    @ToString.Exclude
    @TableField("credential_value")
    private String credentialValue;

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
