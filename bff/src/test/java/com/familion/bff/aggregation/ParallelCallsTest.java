package com.familion.bff.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import com.familion.bff.core.CoreClientException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ParallelCallsTest {

    @Test
    void executesCallsConcurrentlyOnVirtualThreads() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicBoolean ranOnVirtualThread = new AtomicBoolean(false);

        PartialResult<String> first = ParallelCalls.call(() -> {
            ranOnVirtualThread.set(Thread.currentThread().isVirtual());
            bothStarted.countDown();
            awaitQuietly(bothStarted);
            return "accounts";
        });
        PartialResult<Integer> second = ParallelCalls.call(() -> {
            bothStarted.countDown();
            awaitQuietly(bothStarted);
            return 42;
        });

        ParallelCalls.awaitAll(Duration.ofSeconds(2), first, second);

        assertThat(ranOnVirtualThread.get()).isTrue();
        assertThat(first.valueOrNull()).isEqualTo("accounts");
        assertThat(first.unauthorized()).isFalse();
        assertThat(first.status()).contains(200);
        assertThat(second.valueOrNull()).isEqualTo(42);
        assertThat(second.unauthorized()).isFalse();
    }

    @Test
    void detects401WhileAllowingOtherCallsToComplete() {
        PartialResult<String> unauthorizedCall = ParallelCalls.call(() -> {
            throw new CoreClientException(401, "{\"title\":\"Unauthorized\"}");
        });
        PartialResult<String> serverErrorCall = ParallelCalls.call(() -> {
            throw new CoreClientException(503, "{\"title\":\"Unavailable\"}");
        });
        PartialResult<String> okCall = ParallelCalls.call(() -> "ok");

        ParallelCalls.awaitAll(Duration.ofSeconds(2), unauthorizedCall, serverErrorCall, okCall);

        assertThat(unauthorizedCall.unauthorized()).isTrue();
        assertThat(unauthorizedCall.status()).contains(401);
        assertThat(unauthorizedCall.valueOrNull()).isNull();

        assertThat(serverErrorCall.unauthorized()).isFalse();
        assertThat(serverErrorCall.status()).contains(503);
        assertThat(serverErrorCall.valueOrNull()).isNull();

        assertThat(okCall.unauthorized()).isFalse();
        assertThat(okCall.valueOrNull()).isEqualTo("ok");
    }

    @Test
    void timeoutLeavesSlowCallNullWithoutLosingFastCalls() {
        PartialResult<String> slowCall = ParallelCalls.call(() -> {
            try {
                Thread.sleep(Duration.ofSeconds(5));
                return "too-late";
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
        });
        PartialResult<String> fastCall = ParallelCalls.call(() -> "fast-value");

        long startNanos = System.nanoTime();
        ParallelCalls.awaitAll(Duration.ofMillis(200), slowCall, fastCall);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertThat(elapsedMs).isLessThan(1500);
        assertThat(slowCall.valueOrNull()).isNull();
        assertThat(slowCall.unauthorized()).isFalse();
        assertThat(slowCall.status()).isEmpty();
        assertThat(fastCall.valueOrNull()).isEqualTo("fast-value");
    }

    private void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
