package com.x.ai.common.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import tools.jackson.databind.JsonNode;

@Data
@Accessors(chain = true)
public class AgentAppDataSaveDto {

    /**
     * 应用保存的 JSON 值，允许显式保存 null，序列化后最多 1 MiB。
     */
    private JsonNode value;
}
