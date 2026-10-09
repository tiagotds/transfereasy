package br.com.tiagotds.transfereasy.infrastructure.http;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Caps how much work runs at once. With one virtual thread per request the server itself never runs out of
 * threads, so without a cap a burst would queue unboundedly on the database pool and every request would get
 * slow; the bulkhead keeps latency bounded and refuses the excess quickly instead.
 */
final class Bulkhead {

    /** No slot became free in time. */
    static final class Overloaded extends RuntimeException {
        Overloaded() {
            super("Too many concurrent requests", null, false, false);
        }
    }

    private final Semaphore permits;
    private final Duration maxWait;

    Bulkhead(int maxConcurrent, Duration maxWait) {
        this.permits = new Semaphore(maxConcurrent, true);
        this.maxWait = maxWait;
    }

    <T> T call(Callable<T> work) throws Exception {
        if (!acquire()) {
            throw new Overloaded();
        }
        try {
            return work.call();
        } finally {
            permits.release();
        }
    }

    int available() {
        return permits.availablePermits();
    }

    private boolean acquire() {
        try {
            return permits.tryAcquire(maxWait.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
