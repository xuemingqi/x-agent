package com.x.ai.common.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Skill 分享的工具输出，显式固定 ID、状态和时间的 JSON 类型。
 */
@Data
@Accessors(chain = true)
public class SkillShareToolDto {

    private String id;

    private String skillName;

    private String description;

    private String senderMobile;

    private String senderDisplayName;

    /**
     * 状态码：1 待处理，2 已接受，3 已拒绝。
     */
    private int status;

    private List<String> resourcePaths;

    private String createTime;
}
