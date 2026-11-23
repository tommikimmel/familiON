package com.familion.bff.aggregation;

import com.familion.bff.core.CoreClientException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PartialResult<T> {

    private static final Logger log = LoggerFactory.getLogger(PartialResult.class);
    private static final int HTTP_OK = 200;
    private static final int HTTP_UNAUTHORIZED = 401;

    private final Supplier<T> supplier;
    private final AtomicBoolean timedOut = new AtomicBoolean(false);
    private volatile T value;
    private volatile Integer statusCode;
    private volatile boolean unauthorized;

    PartialResult(Supplier<T> supplier) {
        this.supplier = Objects.requireNonNull(supplier, "supplier must not be null");
    }

    public T valueOrNull() {
        return timedOut.get() ? null : value;
    }

    public boolean unauthorized() {
        return !timedOut.get() && unauthorized;
    }

    public Optional<Integer> status() {
        return timedOut.get() ? Optional.empty() : Optional.ofNullable(statusCode);
    }

    void execute() {
        try {
            T result = supplier.get();
            if (!timedOut.get()) {
                this.value = result;
                this.statusCode = HTTP_OK;
            }
        } catch (CoreClientException ex) {
            if (!timedOut.get()) {
                this.value = null;
                this.statusCode = ex.status();
                this.unauthorized = ex.status() == HTTP_UNAUTHORIZED;
            }
        } catch (Exception ex) {
            if (!timedOut.get()) {
                log.warn("Parallel call failed: {}", ex.getMessage());
                this.value = null;
            }
        }
    }

    void markTimedOut() {
        this.timedOut.set(true);
        this.value = null;
        this.statusCode = null;
        this.unauthorized = false;
    }
}
