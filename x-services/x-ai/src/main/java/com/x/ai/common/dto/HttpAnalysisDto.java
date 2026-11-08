package com.x.ai.common.dto;

import com.x.ai.common.domain.HttpExecutionDo;
import com.x.ai.common.domain.HttpLoadResultDo;
import com.x.ai.common.model.HttpRequestSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HttpAnalysisDto {

    /**
     * 用户选择分析的请求配置快照。
     */
    @Valid
    @NotNull
    private HttpRequestSpec request;

    /**
     * 对应请求的实际响应，未执行时为空。
     */
    private HttpExecutionDo response;

    /**
     * 本轮实际压测指标，未压测时为空。
     */
    private HttpLoadResultDo loadTest;

    /**
     * 用户希望重点分析的问题。
     */
    @Size(max = 2000)
    private String question = "";

    /**
     * 可选模型 ID，空值沿用当前用户默认模型。
     */
    @Positive
    private Long modelId;
}
