package com.x.ai.common.dto;

import com.x.ai.common.constants.KnowledgeLinkConstant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "KnowledgeLinkUpdateDto", description = "更新个人在线知识库链接")
public class KnowledgeLinkUpdateDto {

    @PositiveOrZero(message = "文件夹 ID 不能为负数")
    @Schema(name = "folderId", description = "个人文件夹 ID，0 表示移回根目录，未指定则保留原目录")
    private Long folderId;

    @NotBlank(message = "文档名称不能为空")
    @Size(max = KnowledgeLinkConstant.MAX_NAME_LENGTH, message = "文档名称不能超过 256 个字符")
    @Schema(name = "name", description = "文档名称")
    private String name;

    @NotBlank(message = "文档介绍不能为空")
    @Size(max = KnowledgeLinkConstant.MAX_SUMMARY_LENGTH, message = "文档介绍不能超过 1000 个字符")
    @Schema(name = "summary", description = "供 Agent 选择文档的简短介绍")
    private String summary;

    @NotBlank(message = "文档链接不能为空")
    @Size(max = KnowledgeLinkConstant.MAX_URL_LENGTH, message = "文档链接不能超过 2048 个字符")
    @Schema(name = "url", description = "在线文档链接")
    private String url;
}
