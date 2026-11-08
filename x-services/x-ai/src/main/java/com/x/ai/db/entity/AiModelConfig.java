package com.x.ai.db.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.x.framework.common.annotation.LogIgnore;
import com.x.ai.db.enums.AgentModelMultimodalCapability;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * @author : xuemingqi
 * @since : 2026-06-27 14:30
 */
@Data
@Builder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@TableName("ai_model_config")
public class AiModelConfig {

    /**
     * 模型配置ID
     */
    @TableId("id")
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 名称
     */
    @TableField("name")
    private String name;

    /**
     * 模型地址
     */
    @TableField("model_url")
    private String modelUrl;

    /**
     * 模型名称
     */
    @TableField("model_name")
    private String modelName;

    /**
     * 模型 Key
     */
    @LogIgnore
    @TableField("model_key")
    private String modelKey;

    /**
     * 模型上下文窗口大小
     */
    @TableField("context_window_size")
    private Integer contextWindowSize;

    /**
     * 当前用户配置的多模态能力，0 纯文本、1 图片输入、2 图片输出、3 图片输入及输出。
     */
    @TableField("multimodal_capability")
    @Builder.Default
    private AgentModelMultimodalCapability multimodalCapability = AgentModelMultimodalCapability.NONE;

    /**
     * 模型温度，空值表示使用模型提供方默认值。
     */
    @TableField(value = "temperature", updateStrategy = FieldStrategy.ALWAYS)
    private Double temperature;

    /**
     * 是否为外部调用使用的默认模型
     */
    @TableField("is_default")
    private Boolean isDefault;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
