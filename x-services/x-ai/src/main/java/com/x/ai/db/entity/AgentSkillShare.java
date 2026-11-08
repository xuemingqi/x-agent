package com.x.ai.db.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.x.ai.db.enums.AgentSkillShareStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("agent_skill_share")
public class AgentSkillShare {

    /**
     * 分享邀请 ID。
     */
    @TableId("id")
    private Long id;

    /**
     * 分享时生成的 Skill 快照 ID。
     */
    @TableField("skill_id")
    private Long skillId;

    /**
     * 分享发起人用户 ID。
     */
    @TableField("sender_user_id")
    private String senderUserId;

    /**
     * 发起分享时的发送人手机号。
     */
    @TableField("sender_mobile")
    private String senderMobile;

    /**
     * 发起分享时的发送人名称快照。
     */
    @TableField("sender_display_name")
    private String senderDisplayName;

    /**
     * 分享接收人用户 ID。
     */
    @TableField("recipient_user_id")
    private String recipientUserId;

    /**
     * 分享时规范化后的接收人手机号。
     */
    @TableField("recipient_mobile")
    private String recipientMobile;

    /**
     * 发起分享时的个人 Skill 名称。
     */
    @TableField("source_skill_name")
    private String sourceSkillName;

    /**
     * 邀请状态：1 待处理，2 已接受，3 已拒绝。
     */
    @TableField("status")
    private AgentSkillShareStatus status;

    /**
     * 接收人接受或拒绝邀请的时间，未处理时为空。
     */
    @TableField("response_time")
    private LocalDateTime responseTime;

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
