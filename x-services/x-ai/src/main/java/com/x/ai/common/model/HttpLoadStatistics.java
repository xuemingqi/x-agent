package com.x.ai.common.model;

import com.x.ai.common.domain.HttpExecutionDo;
import com.x.ai.common.domain.HttpLoadPointDo;
import com.x.ai.common.domain.HttpLoadResultDo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 一轮压测的测量聚合，不承担调度、鉴权或运行生命周期。
 */
public class HttpLoadStatistics {

    /**
     * 完成的请求数量。
     */
    private int completed;

    /**
     * 非 2xx、网络错误和超限响应数量。
     */
    private int failed;

    /**
     * 已完成请求总耗时，单位毫秒。
     */
    private double totalMs;

    /**
     * 每毫秒一个桶的延迟分布。
     */
    private final Map<Integer, Integer> histogram = new TreeMap<>();

    /**
     * HTTP 状态码及网络错误分布。
     */
    private final Map<String, Integer> statusCounts = new TreeMap<>();

    /**
     * 按完成时间聚合的秒级指标。
     */
    private final Map<Integer, HttpLoadPointDo> timeline = new TreeMap<>();

    public void record(HttpExecutionDo result, int second) {
        boolean unsuccessful = result.getStatusCode() < 200 || result.getStatusCode() >= 300
                || !result.getError().isEmpty() || result.isTruncated();
        String status = !result.getError().isEmpty() ? result.getError()
                : result.isTruncated() ? "响应正文超限" : String.valueOf(result.getStatusCode());
        completed++;
        failed += unsuccessful ? 1 : 0;
        totalMs += result.getElapsedMs();
        histogram.merge((int) Math.ceil(result.getElapsedMs()), 1, Integer::sum);
        statusCounts.merge(status, 1, Integer::sum);
        HttpLoadPointDo point = timeline.computeIfAbsent(second, value -> new HttpLoadPointDo().setSecond(value));
        point.setAverageMs((point.getAverageMs() * point.getCompleted() + result.getElapsedMs())
                / (point.getCompleted() + 1));
        point.setCompleted(point.getCompleted() + 1).setFailed(point.getFailed() + (unsuccessful ? 1 : 0));
    }

    public HttpLoadResultDo snapshot(double seconds) {
        return new HttpLoadResultDo().setCompleted(completed).setFailed(failed)
                .setThroughput(seconds > 0 ? completed / seconds : 0)
                .setErrorRate(completed == 0 ? 0 : failed * 100.0 / completed)
                .setAverageMs(completed == 0 ? 0 : totalMs / completed)
                .setP50Ms(percentile(0.5)).setP95Ms(percentile(0.95)).setP99Ms(percentile(0.99))
                .setStatusCounts(new LinkedHashMap<>(statusCounts))
                .setTimeline(timeline.values().stream().map(point -> new HttpLoadPointDo().setSecond(point.getSecond())
                        .setCompleted(point.getCompleted()).setFailed(point.getFailed())
                        .setAverageMs(point.getAverageMs())).toList());
    }

    /**
     * 按全部完成请求的 nearest-rank 计算百分位，不平均每秒百分位。
     */
    private int percentile(double fraction) {
        int target = (int) Math.ceil(completed * fraction);
        int count = 0;
        for (var bucket : histogram.entrySet()) {
            count += bucket.getValue();
            if (count >= target) {
                return bucket.getKey();
            }
        }
        return 0;
    }
}
