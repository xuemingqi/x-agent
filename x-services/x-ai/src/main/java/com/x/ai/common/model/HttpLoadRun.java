package com.x.ai.common.model;

import com.x.ai.common.domain.HttpExecutionDo;
import com.x.ai.common.domain.HttpLoadResultDo;
import com.x.ai.common.dto.HttpLoadTestDto;
import com.x.ai.common.enums.HttpLoadStatus;
import lombok.Getter;
import reactor.core.Disposable;

import java.util.UUID;

/**
 * 一轮压测拥有自己的订阅和统计，锁保护终态与样本提交之间的竞争。
 */
public class HttpLoadRun {

    /**
     * 运行标识。
     */
    @Getter
    private final String id = UUID.randomUUID().toString();

    /**
     * 运行所属登录用户。
     */
    @Getter
    private final String userId;

    /**
     * 本轮固定使用的请求与发压配置。
     */
    @Getter
    private final HttpLoadTestDto configuration;

    /**
     * 本轮统计只由当前运行同步访问。
     */
    private final HttpLoadStatistics statistics = new HttpLoadStatistics();

    /**
     * 单调时钟起点。
     */
    private final long startedNanos = System.nanoTime();

    /**
     * 已开始的网络调用数量。
     */
    private int started;

    /**
     * 运行状态，只能从运行中进入终态。
     */
    private HttpLoadStatus status = HttpLoadStatus.RUNNING;

    /**
     * 终态耗时，单位纳秒。
     */
    private long elapsedNanos;

    /**
     * 短期内存清理使用的结束时间。
     */
    @Getter
    private volatile long finishedAt;

    /**
     * 执行失败时给用户的说明。
     */
    private String message = "";

    /**
     * 当前响应式执行订阅，停止时取消全部在途请求。
     */
    private Disposable subscription;

    public HttpLoadRun(String userId, HttpLoadTestDto configuration) {
        this.userId = userId;
        this.configuration = configuration;
    }

    public synchronized void attach(Disposable subscription) {
        if (status != HttpLoadStatus.RUNNING) {
            subscription.dispose();
        } else {
            this.subscription = subscription;
        }
    }

    public synchronized boolean begin() {
        if (status != HttpLoadStatus.RUNNING) {
            return false;
        }
        started++;
        return true;
    }

    public synchronized void record(HttpExecutionDo result) {
        if (status == HttpLoadStatus.RUNNING) {
            statistics.record(result, (int) ((System.nanoTime() - startedNanos) / 1_000_000_000L));
        }
    }

    public synchronized void finish(HttpLoadStatus terminalStatus, String message) {
        if (status == HttpLoadStatus.RUNNING) {
            status = terminalStatus;
            this.message = message;
            elapsedNanos = System.nanoTime() - startedNanos;
            finishedAt = System.currentTimeMillis();
        }
    }

    public synchronized void stop() {
        finish(HttpLoadStatus.STOPPED, "用户停止了本轮压测");
        if (subscription != null) {
            subscription.dispose();
        }
    }

    public synchronized HttpLoadResultDo snapshot() {
        double seconds = (status == HttpLoadStatus.RUNNING ? System.nanoTime() - startedNanos : elapsedNanos)
                / 1_000_000_000.0;
        HttpLoadResultDo result = statistics.snapshot(seconds);
        return result.setId(id).setStatus(status).setMethod(configuration.getRequest().getMethod())
                .setUrl(configuration.getRequest().getUrl()).setConcurrency(configuration.getConcurrency())
                .setDurationSeconds(configuration.getDurationSeconds()).setRequestsPerSecond(configuration.getRequestsPerSecond())
                .setElapsedSeconds(seconds).setStarted(started)
                .setCancelled(status == HttpLoadStatus.RUNNING ? 0 : started - result.getCompleted()).setMessage(message);
    }
}
