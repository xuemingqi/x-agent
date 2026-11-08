package com.x.ai.common.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@JsonClassDescription("根据请求配置和实际执行指标生成的 HTTP 分析")
public class HttpAnalysisDo {

    /**
     * 中文概括请求或实际测量结果；未执行时明确说明仅分析配置。
     */
    @NotBlank
    @Size(max = 1000)
    @JsonProperty(required = true)
    @JsonPropertyDescription("中文概括请求或实际测量结果；未执行时明确说明仅分析配置")
    private String summary;

    /**
     * 最多10条有实际输入依据的发现，注明相关状态码、字段或指标。
     */
    @NotNull
    @Size(max = 10)
    @JsonProperty(required = true)
    @JsonPropertyDescription("最多10条有实际输入依据的发现，注明相关状态码、字段或指标")
    private List<@NotBlank @Size(max = 1000) String> observations;

    /**
     * 最多10条具体建议；推测必须标注为待验证，不自动修改或执行请求。
     */
    @NotNull
    @Size(max = 10)
    @JsonProperty(required = true)
    @JsonPropertyDescription("最多10条具体建议；推测必须标注为待验证，不自动修改或执行请求")
    private List<@NotBlank @Size(max = 1000) String> suggestions;

    /**
     * 说明截断正文、样本不足、单点发压、未采集服务端指标等实际限制。
     */
    @NotNull
    @Size(max = 10)
    @JsonProperty(required = true)
    @JsonPropertyDescription("说明截断正文、样本不足、单点发压、未采集服务端指标等实际限制")
    private List<@NotBlank @Size(max = 1000) String> limitations;
}
