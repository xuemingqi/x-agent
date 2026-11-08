package com.x.ai.common.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HttpExecutionDo {

    /**
     * 实际发送的完整 URL。
     */
    private String url;

    /**
     * HTTP 状态码；未收到响应头时为 0。
     */
    private int statusCode;

    /**
     * 包括读取响应体在内的总耗时，单位毫秒。
     */
    private double elapsedMs;

    /**
     * 实际读取的响应正文大小，截断时为已读取字节数。
     */
    private long responseBytes;

    /**
     * 正文是否超过读取上限。
     */
    private boolean truncated;

    /**
     * 文本响应正文；二进制响应显示类型说明。
     */
    private String body = "";

    /**
     * 响应头，保留同名头的全部值。
     */
    private Map<String, List<String>> headers = new LinkedHashMap<>();

    /**
     * 网络或读取失败说明，正常收到完整响应时为空。
     */
    private String error = "";

}
