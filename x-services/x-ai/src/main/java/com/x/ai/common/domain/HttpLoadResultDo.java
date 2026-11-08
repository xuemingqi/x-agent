package com.x.ai.common.domain;

import com.x.ai.common.enums.HttpLoadStatus;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class HttpLoadResultDo {

    /**
     * 压测记录 ID，仅所属用户可访问。
     */
    private String id;

    /**
     * 当前运行状态。
     */
    private HttpLoadStatus status;

    /**
     * 本轮固定请求方法。
     */
    private String method;

    /**
     * 本轮请求地址。
     */
    private String url;

    /**
     * 本轮并发请求数上限。
     */
    private int concurrency;

    /**
     * 配置的发压时长，单位秒。
     */
    private int durationSeconds;

    /**
     * 本轮总请求速率上限。
     */
    private int requestsPerSecond;

    /**
     * 从启动到当前或结束的实际时长，单位秒。
     */
    private double elapsedSeconds;

    /**
     * 已发出的请求数。
     */
    private int started;

    /**
     * 已完成请求数，不含取消。
     */
    private int completed;

    /**
     * 非 2xx、网络错误或正文超限的完成请求数。
     */
    private int failed;

    /**
     * 用户停止或时长到期取消的请求数。
     */
    private int cancelled;

    /**
     * 已完成请求数除以实际运行秒数。
     */
    private double throughput;

    /**
     * 失败请求占已完成请求的百分比。
     */
    private double errorRate;

    /**
     * 已完成请求平均总耗时，单位毫秒。
     */
    private double averageMs;

    /**
     * 已完成请求耗时 P50，毫秒，按 1ms 桶向上取整。
     */
    private int p50Ms;

    /**
     * 已完成请求耗时 P95，毫秒。
     */
    private int p95Ms;

    /**
     * 已完成请求耗时 P99，毫秒。
     */
    private int p99Ms;

    /**
     * 状态码与网络错误类型的计数。
     */
    private Map<String, Integer> statusCounts;

    /**
     * 按秒聚合的完成请求时间序列。
     */
    private List<HttpLoadPointDo> timeline;

    /**
     * 结束原因或运行限制说明。
     */
    private String message = "";
}
