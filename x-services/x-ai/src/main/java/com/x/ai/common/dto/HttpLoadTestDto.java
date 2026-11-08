package com.x.ai.common.dto;

import com.x.ai.common.model.HttpRequestSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HttpLoadTestDto {

    /**
     * 本轮压测使用的固定请求快照。
     */
    @Valid
    @NotNull
    private HttpRequestSpec request;

    /**
     * 同时执行请求的用户数。
     */
    @Min(1)
    @Max(20)
    private int concurrency = 5;

    /**
     * 发压时长，单位秒。
     */
    @Min(1)
    @Max(120)
    private int durationSeconds = 30;

    /**
     * 整轮压测的每秒请求上限，实际吞吐由目标响应时间决定。
     */
    @Min(1)
    @Max(100)
    private int requestsPerSecond = 10;
}
