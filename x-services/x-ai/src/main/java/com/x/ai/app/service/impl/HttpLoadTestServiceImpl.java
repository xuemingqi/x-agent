package com.x.ai.app.service.impl;

import com.x.ai.app.service.HttpLoadTestService;
import com.x.ai.app.service.HttpRequestExecutionService;
import com.x.ai.common.domain.HttpExecutionDo;
import com.x.ai.common.domain.HttpLoadResultDo;
import com.x.ai.common.dto.HttpLoadTestDto;
import com.x.ai.common.enums.HttpLoadStatus;
import com.x.ai.common.model.HttpLoadRun;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.JsonUtil;
import com.x.framework.common.utils.UserThreadLocalUtil;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class HttpLoadTestServiceImpl implements HttpLoadTestService {

    private static final int MAX_ACTIVE_RUNS = 4;

    private static final int MAX_REQUESTS = 10000;

    private static final int MAX_RECORDS = 32;

    private static final long RESULT_RETENTION_MS = Duration.ofMinutes(5).toMillis();

    private final HttpRequestExecutionService requestExecutionService;

    private final Map<String, HttpLoadRun> runs = new ConcurrentHashMap<>();


    @Override
    public synchronized HttpLoadResultDo start(HttpLoadTestDto request) {
        String userId = UserThreadLocalUtil.getUserId();
        reserveRunCapacity(userId);
        HttpLoadTestDto snapshot = JsonUtil.OBJECT_MAPPER.readValue(JsonUtil.toJsonStrOrThrow(request), HttpLoadTestDto.class);
        HttpLoadRun run = new HttpLoadRun(userId, snapshot);
        runs.put(run.getId(), run);
        run.attach(execute(run).subscribe(run::record,
                error -> run.finish(HttpLoadStatus.FAILED, "压测执行失败，请检查请求配置和执行资源"),
                () -> run.finish(HttpLoadStatus.COMPLETED, "已达到配置时长或单轮请求上限")));
        return run.snapshot();
    }

    @Override
    public HttpLoadResultDo get(String id) {
        return ownedRun(id).snapshot();
    }

    @Override
    public HttpLoadResultDo stop(String id) {
        HttpLoadRun run = ownedRun(id);
        run.stop();
        return run.snapshot();
    }

    /**
     * 固定速率产生请求时隙，并发耗尽时跳过时隙；不积压和突发补发。
     */
    private Flux<HttpExecutionDo> execute(HttpLoadRun run) {
        HttpLoadTestDto configuration = run.getConfiguration();
        Duration interval = Duration.ofNanos(1_000_000_000L / configuration.getRequestsPerSecond());
        long requestLimit = Math.min(MAX_REQUESTS,
                (long) configuration.getRequestsPerSecond() * configuration.getDurationSeconds());
        return Flux.interval(Duration.ZERO, interval).onBackpressureDrop().take(requestLimit)
                .flatMap(tick -> Mono.defer(() -> run.begin()
                                ? requestExecutionService.execute(configuration.getRequest()) : Mono.empty()),
                        configuration.getConcurrency(), 1)
                .take(Duration.ofSeconds(configuration.getDurationSeconds()));
    }

    /**
     * 每用户同时一轮，每节点同时四轮；终态仅为轮询短暂保留，不持久化。
     */
    private void reserveRunCapacity(String userId) {
        cleanup();
        var active = runs.values().stream().filter(run -> run.getFinishedAt() == 0).toList();
        if (active.size() >= MAX_ACTIVE_RUNS || active.stream().anyMatch(run -> run.getUserId().equals(userId))) {
            throw new XException(ResponseCodeEnum.HTTP_LAB_BUSY);
        }
        if (runs.size() >= MAX_RECORDS) {
            runs.values().stream().filter(run -> run.getFinishedAt() != 0)
                    .min(Comparator.comparingLong(HttpLoadRun::getFinishedAt)).ifPresent(run -> runs.remove(run.getId()));
        }
    }

    /**
     * 读取与停止都依据当前登录身份检查运行归属。
     */
    private HttpLoadRun ownedRun(String id) {
        cleanup();
        HttpLoadRun run = runs.get(id);
        if (run == null || !run.getUserId().equals(UserThreadLocalUtil.getUserId())) {
            throw new XException(ResponseCodeEnum.HTTP_LAB_NOT_FOUND);
        }
        return run;
    }

    /**
     * 清理已结束的运行对象，不清理正在发压的任务。
     */
    private void cleanup() {
        long cutoff = System.currentTimeMillis() - RESULT_RETENTION_MS;
        runs.values().removeIf(run -> run.getFinishedAt() != 0 && run.getFinishedAt() < cutoff);
    }

    @PreDestroy
    public void close() {
        runs.values().forEach(HttpLoadRun::stop);
    }
}
