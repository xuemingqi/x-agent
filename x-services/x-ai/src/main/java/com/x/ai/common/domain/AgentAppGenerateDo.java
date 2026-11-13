package com.x.ai.common.domain;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AgentAppGenerateDo {

    /**
     * 模型生成的文本；不包含模型配置或凭证。
     */
    private String text;
}
