package com.x.ai.app.service.impl;

import com.x.ai.app.service.HttpRequestExecutionService;
import com.x.ai.common.domain.HttpExecutionDo;
import com.x.ai.common.dto.HttpLoadTestDto;
import com.x.ai.common.enums.HttpLoadStatus;
import com.x.ai.common.model.HttpLoadStatistics;
import com.x.ai.common.model.HttpLoadRun;
import com.x.ai.common.model.HttpRequestSpec;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.framework.common.exception.XException;
import com.x.framework.common.enums.ResponseCodeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class HttpLoadTestServiceImplTest {

    @AfterEach
    void clearIdentity() {
        UserThreadLocalUtil.clear();
    }

    @Test
    void computesPercentilesFromAllSamplesAndCountsHttpAndTransportFailures() {
        HttpLoadStatistics statistics = new HttpLoadStatistics();
        for (int index = 1; index <= 100; index++) {
            statistics.record(new HttpExecutionDo().setElapsedMs(index).setStatusCode(index <= 90 ? 200 : 500), index / 20);
        }
        var result = statistics.snapshot(10);
        assertThat(result.getP50Ms()).isEqualTo(50);
        assertThat(result.getP95Ms()).isEqualTo(95);
        assertThat(result.getP99Ms()).isEqualTo(99);
        assertThat(result.getAverageMs()).isEqualTo(50.5);
        assertThat(result.getThroughput()).isEqualTo(10);
        assertThat(result.getErrorRate()).isEqualTo(10);
        assertThat(result.getStatusCounts()).containsEntry("200", 90).containsEntry("500", 10);
        statistics.record(new HttpExecutionDo().setError("请求总超时").setElapsedMs(1000), 6);
        assertThat(statistics.snapshot(10).getFailed()).isEqualTo(11);
    }

    @Test
    void enforcesConcurrencyAndOwnershipAndCancelsInFlightRequestsOnStop() throws Exception {
        UserThreadLocalUtil.set(UserInfo.builder().userId("first").build());
        HttpRequestExecutionService executor = mock(HttpRequestExecutionService.class);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch cancelled = new CountDownLatch(2);
        when(executor.execute(any())).thenReturn(Mono.<HttpExecutionDo>never()
                .doOnSubscribe(subscription -> started.countDown()).doOnCancel(cancelled::countDown));
        HttpLoadTestServiceImpl service = new HttpLoadTestServiceImpl(executor);
        try {
            var request = new HttpLoadTestDto().setRequest(new HttpRequestSpec().setUrl("http://example.test"))
                    .setConcurrency(2).setRequestsPerSecond(100).setDurationSeconds(30);
            var run = service.start(request);
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(service.get(run.getId()).getStarted()).isEqualTo(2);
            request.setConcurrency(20).getRequest().setUrl("http://changed.example.test");
            assertThat(service.get(run.getId()).getConcurrency()).isEqualTo(2);
            assertThat(service.get(run.getId()).getUrl()).isEqualTo("http://example.test");
            assertThatThrownBy(() -> service.start(request)).isInstanceOf(XException.class);
            UserThreadLocalUtil.set(UserInfo.builder().userId("second").build());
            assertThatThrownBy(() -> service.get(run.getId()))
                    .isInstanceOfSatisfying(XException.class, error -> assertThat(error.getResponseCodeEnum())
                            .isEqualTo(ResponseCodeEnum.HTTP_LAB_NOT_FOUND));
            assertThatThrownBy(() -> service.stop(run.getId())).isInstanceOf(XException.class);
            UserThreadLocalUtil.set(UserInfo.builder().userId("first").build());
            var stopped = service.stop(run.getId());
            assertThat(cancelled.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(stopped.getStatus()).isEqualTo(HttpLoadStatus.STOPPED);
            assertThat(stopped.getCancelled()).isEqualTo(2);
            assertThat(stopped.getCompleted()).isZero();
            assertThat(service.stop(run.getId()).getElapsedSeconds()).isEqualTo(stopped.getElapsedSeconds());
        } finally {
            service.close();
        }
    }

    @Test
    void completesAtDurationAndCancelsSlowRequestsWithoutCountingThemAsFailedSamples() {
        UserThreadLocalUtil.set(UserInfo.builder().userId("user").build());
        HttpRequestExecutionService executor = mock(HttpRequestExecutionService.class);
        when(executor.execute(any())).thenReturn(Mono.never());
        HttpLoadTestServiceImpl service = new HttpLoadTestServiceImpl(executor);
        try {
            var run = service.start(new HttpLoadTestDto().setRequest(new HttpRequestSpec().setUrl("http://example.test"))
                    .setConcurrency(1).setRequestsPerSecond(10).setDurationSeconds(1));
            await().pollInSameThread().atMost(Duration.ofSeconds(3))
                    .until(() -> service.get(run.getId()).getStatus() == HttpLoadStatus.COMPLETED);
            var result = service.get(run.getId());
            assertThat(result.getStarted()).isEqualTo(1);
            assertThat(result.getCancelled()).isEqualTo(1);
            assertThat(result.getFailed()).isZero();
        } finally {
            service.close();
        }
    }

    @Test
    void stoppingBeforeSubscriptionAttachmentCancelsTheSubscriptionAndIgnoresLateSamples() {
        HttpLoadRun run = new HttpLoadRun("owner", new HttpLoadTestDto()
                .setRequest(new HttpRequestSpec().setUrl("https://example.test")));
        assertThat(run.begin()).isTrue();
        run.stop();
        Disposable subscription = mock(Disposable.class);
        run.attach(subscription);
        run.record(new HttpExecutionDo().setStatusCode(200).setElapsedMs(10));
        run.finish(HttpLoadStatus.COMPLETED, "late completion");

        verify(subscription).dispose();
        assertThat(run.begin()).isFalse();
        assertThat(run.snapshot().getStatus()).isEqualTo(HttpLoadStatus.STOPPED);
        assertThat(run.snapshot().getCompleted()).isZero();
        assertThat(run.snapshot().getCancelled()).isEqualTo(1);
    }

    @Test
    void limitsTheNodeToFourActiveRunsAcrossDifferentUsers() {
        HttpRequestExecutionService executor = mock(HttpRequestExecutionService.class);
        when(executor.execute(any())).thenReturn(Mono.never());
        HttpLoadTestServiceImpl service = new HttpLoadTestServiceImpl(executor);
        HttpLoadTestDto request = new HttpLoadTestDto()
                .setRequest(new HttpRequestSpec().setUrl("https://example.test"));
        try {
            for (int user = 1; user <= 4; user++) {
                UserThreadLocalUtil.set(UserInfo.builder().userId(String.valueOf(user)).build());
                assertThat(service.start(request).getStatus()).isEqualTo(HttpLoadStatus.RUNNING);
            }
            UserThreadLocalUtil.set(UserInfo.builder().userId("5").build());
            assertThatThrownBy(() -> service.start(request))
                    .isInstanceOfSatisfying(XException.class, error -> assertThat(error.getResponseCodeEnum())
                            .isEqualTo(ResponseCodeEnum.HTTP_LAB_BUSY));
        } finally {
            service.close();
        }
    }

    @Test
    void neverSchedulesAnExtraRequestAtTheDurationBoundary() {
        UserThreadLocalUtil.set(UserInfo.builder().userId("1").build());
        HttpRequestExecutionService executor = mock(HttpRequestExecutionService.class);
        when(executor.execute(any())).thenReturn(Mono.just(new HttpExecutionDo().setStatusCode(200)));
        HttpLoadTestServiceImpl service = new HttpLoadTestServiceImpl(executor);
        try {
            var run = service.start(new HttpLoadTestDto()
                    .setRequest(new HttpRequestSpec().setUrl("https://example.test"))
                    .setConcurrency(1).setRequestsPerSecond(5).setDurationSeconds(1));
            await().pollInSameThread().atMost(Duration.ofSeconds(3))
                    .until(() -> service.get(run.getId()).getStatus() == HttpLoadStatus.COMPLETED);

            assertThat(service.get(run.getId()).getStarted()).isEqualTo(5);
            assertThat(service.get(run.getId()).getCompleted()).isEqualTo(5);
            verify(executor, times(5)).execute(any());
        } finally {
            service.close();
        }
    }
}
