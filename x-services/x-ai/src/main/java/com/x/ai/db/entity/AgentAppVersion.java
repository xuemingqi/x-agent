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
@TableName("agent_app_version")
public class AgentAppVersion {

    /**
     * 不可变版本 ID。
     */
    @TableId("id")
    private String id;

    /**
     * 版本所属应用 ID。
     */
    @TableField("app_id")
    private String appId;

    /**
     * 版本发布时的展示名称。
     */
    @TableField("name")
    private String name;

    /**
     * 版本发布时的能力介绍。
     */
    @TableField("description")
    private String description;

    /**
     * 私有 MinIO Bucket 中应用包的对象键。
     */
    @TableField("object_key")
    private String objectKey;

    /**
     * 应用包 UTF-8 字节数。
     */
    @TableField("size_bytes")
    private Long sizeBytes;

    /**
     * 应用包 SHA-256 摘要，用于读取完整性校验。
     */
    @TableField("sha256")
    private String sha256;

    /**
     * 版本写入时间，由数据库维护。
     */
    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;
}
