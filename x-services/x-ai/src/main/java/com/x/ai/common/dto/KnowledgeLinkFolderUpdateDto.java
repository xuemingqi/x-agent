package com.x.ai.common.dto;

import com.x.ai.common.constants.KnowledgeLinkConstant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "KnowledgeLinkFolderUpdateDto", description = "重命名个人在线知识库文件夹")
public class KnowledgeLinkFolderUpdateDto {

    @NotBlank(message = "文件夹名称不能为空")
    @Size(max = KnowledgeLinkConstant.MAX_NAME_LENGTH, message = "文件夹名称不能超过 256 个字符")
    @Schema(name = "name", description = "文件夹名称")
    private String name;
}
