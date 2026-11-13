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
@TableName("agent_app_data")
public class AgentAppData {

    /**
     * 应用数据记录 ID。
     */
    @TableId("id")
    private Long id;

    /**
     * 数据所属用户，不接受应用代码传入的身份。
     */
    @TableField("owner_user_id")
    private String ownerUserId;

    /**
     * 数据所属动态应用 ID，删除应用时级联清理。
     */
    @TableField("app_id")
    private String appId;

    /**
     * 应用内的大小写敏感键，最多 128 个 ASCII 字符。
     */
    @TableField("data_key")
    private String dataKey;

    /**
     * 序列化 JSON 值，最多 1 MiB。
     */
    @TableField("value_json")
    private String valueJson;

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
