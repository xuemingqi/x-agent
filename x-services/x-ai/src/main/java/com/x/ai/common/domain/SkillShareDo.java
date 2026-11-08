package com.x.ai.common.domain;

import com.x.ai.db.enums.AgentSkillShareStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
@Schema(name = "SkillShareDo", description = "Skill 分享邀请")
public class SkillShareDo {

    @Schema(name = "id", description = "分享邀请 ID，使用字符串避免前端整数精度丢失")
    private String id;

    @Schema(name = "skillName", description = "Skill 名称")
    private String skillName;

    @Schema(name = "description", description = "Skill 使用场景描述")
    private String description;

    @Schema(name = "senderMobile", description = "分享人手机号")
    private String senderMobile;

    @Schema(name = "senderDisplayName", description = "分享人展示名称")
    private String senderDisplayName;

    @Schema(name = "status", description = "分享邀请状态")
    private AgentSkillShareStatus status;

    @Schema(name = "resourcePaths", description = "Skill 附属资源相对路径")
    private List<String> resourcePaths;

    @Schema(name = "createTime", description = "分享时间")
    private LocalDateTime createTime;
}
