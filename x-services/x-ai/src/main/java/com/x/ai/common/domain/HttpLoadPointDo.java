package com.x.ai.common.domain;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HttpLoadPointDo {

    /**
     * 相对本轮开始时间的秒数。
     */
    private int second;

    /**
     * 该秒完成的请求数。
     */
    private int completed;

    /**
     * 该秒失败的请求数。
     */
    private int failed;

    /**
     * 该秒完成请求的平均总耗时，单位毫秒。
     */
    private double averageMs;
}
