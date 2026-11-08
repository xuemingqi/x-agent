package com.x.ai.common.domain;

import com.x.ai.db.enums.AgentKnowledgeLinkScope;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@Schema(name = "KnowledgeLinkDo", description = "当前用户可见的在线知识库链接")
public class KnowledgeLinkDo {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(name = "id", description = "在线知识库链接 ID")
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(name = "folderId", description = "所属文件夹 ID，0 表示根目录")
    private Long folderId;

    @Schema(name = "name", description = "文档名称")
    private String name;

    @Schema(name = "summary", description = "文档简短介绍")
    private String summary;

    @Schema(name = "url", description = "在线文档链接")
    private String url;

    @Schema(name = "scope", description = "作用域：1 公共，2 个人")
    private AgentKnowledgeLinkScope scope;

    @Schema(name = "editable", description = "当前用户是否可以编辑")
    private boolean editable;

    @Schema(name = "createTime", description = "创建时间")
    private LocalDateTime createTime;

    @Schema(name = "updateTime", description = "更新时间")
    private LocalDateTime updateTime;
}
