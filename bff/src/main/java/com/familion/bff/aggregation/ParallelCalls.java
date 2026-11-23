package com.familion.bff.aggregation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public final class ParallelCalls {

    private ParallelCalls() {
    }

    public static <T> PartialResult<T> call(Supplier<T> supplier) {
        return new PartialResult<>(supplier);
    }

    public static void awaitAll(Duration timeout, PartialResult<?>... calls) {
        if (calls == null || calls.length == 0) {
            return;
        }
        Duration effectiveTimeout = timeout == null || timeout.isNegative()
                ? Duration.ZERO
                : timeout;

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<PartialResult<?>> activeCalls = new ArrayList<>(calls.length);
            List<Future<?>> futures = new ArrayList<>(calls.length);
            for (PartialResult<?> call : calls) {
                if (call != null) {
                    activeCalls.add(call);
                    futures.add(executor.submit(call::execute));
                }
            }

            long deadlineNanos = System.nanoTime() + effectiveTimeout.toNanos();
            for (int i = 0; i < futures.size(); i++) {
                Future<?> future = futures.get(i);
                PartialResult<?> call = activeCalls.get(i);
                if (future.isDone()) {
                    continue;
                }
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    call.markTimedOut();
                    future.cancel(true);
                    continue;
                }
                try {
                    future.get(remainingNanos, TimeUnit.NANOSECONDS);
                } catch (TimeoutException ex) {
                    call.markTimedOut();
                    future.cancel(true);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    call.markTimedOut();
                    future.cancel(true);
                } catch (ExecutionException | CancellationException ex) {
                    // Errors are captured inside PartialResult.execute()
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
