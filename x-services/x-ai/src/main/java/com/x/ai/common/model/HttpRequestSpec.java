package com.x.ai.common.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class HttpRequestSpec {

    /**
     * HTTP 方法，不支持建立隧道的 CONNECT。
     */
    @NotNull
    @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS")
    private String method = "GET";

    /**
     * HTTP 或 HTTPS 绝对地址，可带已有查询参数。
     */
    @NotBlank
    @Size(max = 4096)
    private String url;

    /**
     * 追加的查询参数，保留同名参数及顺序。
     */
    @Valid
    @NotNull
    @Size(max = 80)
    private List<@NotNull HttpRequestField> query = new ArrayList<>();

    /**
     * 显式发送的请求头，不继承平台登录凭证。
     */
    @Valid
    @NotNull
    @Size(max = 80)
    private List<@NotNull HttpRequestField> headers = new ArrayList<>();

    /**
     * UTF-8 原始正文，GET 和 HEAD 不发送正文。
     */
    @NotNull
    @Size(max = 131072)
    private String body = "";

    /**
     * 单次请求总超时，单位毫秒，包含响应体读取。
     */
    @Min(100)
    @Max(30000)
    private int timeoutMs = 10000;
}
