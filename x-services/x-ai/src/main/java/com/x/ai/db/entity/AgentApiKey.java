package com.x.ai.db.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.x.framework.common.annotation.LogIgnore;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 用户 Open API Key。
 *
 * @author xuemingqi
 */
@Data
@Accessors(chain = true)
@TableName("agent_api_key")
public class AgentApiKey {

    /**
     * API Key 所属用户 ID，每个用户只保留一个当前 Key。
     */
    @TableId("user_id")
    private String userId;

    /**
     * API Key 的 SHA-256 摘要，数据库不保存明文。
     */
    @ToString.Exclude
    @LogIgnore
    @TableField("key_hash")
    private String keyHash;

    /**
     * 创建时间。
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 最近一次轮换时间。
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
