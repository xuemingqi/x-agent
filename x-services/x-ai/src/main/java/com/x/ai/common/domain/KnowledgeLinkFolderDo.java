package com.x.ai.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@Data
@Accessors(chain = true)
@Schema(name = "KnowledgeLinkFolderDo", description = "当前用户可见的在线知识库文件夹")
public class KnowledgeLinkFolderDo {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(name = "id", description = "文件夹 ID")
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(name = "parentId", description = "父文件夹 ID，0 表示根目录")
    private Long parentId;

    @Schema(name = "name", description = "文件夹名称")
    private String name;

}
