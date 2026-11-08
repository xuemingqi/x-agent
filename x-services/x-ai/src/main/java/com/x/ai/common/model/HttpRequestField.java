package com.x.ai.common.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HttpRequestField {

    /**
     * 请求头或查询参数名称，空名称不会发送。
     */
    @NotNull
    @Size(max = 256)
    private String name = "";

    /**
     * 字段原始值，查询参数由执行器编码。
     */
    @NotNull
    @Size(max = 8192)
    private String value = "";

    /**
     * 是否在本次请求中启用。
     */
    private boolean enabled = true;
}
