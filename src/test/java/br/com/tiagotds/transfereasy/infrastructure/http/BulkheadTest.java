package br.com.tiagotds.transfereasy.infrastructure.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BulkheadTest {

    @Test
    void work_runs_when_a_permit_is_free() throws Exception {
        var bulkhead = new Bulkhead(1, Duration.ZERO);

        assertEquals("ok", bulkhead.call(() -> "ok"));
        assertEquals(1, bulkhead.available());
    }

    @Test
    void when_every_permit_is_busy_for_longer_than_the_wait_the_caller_is_refused_fast() throws Exception {
        var bulkhead = new Bulkhead(2, Duration.ofMillis(50));
        var busy = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> bulkhead.call(() -> {
                    busy.countDown();
                    release.await();
                    return null;
                }));
            }
            assertTrue(busy.await(5, TimeUnit.SECONDS));

            var start = System.nanoTime();
            assertThrows(Bulkhead.Overloaded.class, () -> bulkhead.call(() -> "never"));
            assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2), "refusal must not hang");

            release.countDown();
        }
        assertEquals(2, bulkhead.available(), "permits come back when the work ends");
    }

    @Test
    void a_waiting_caller_gets_in_as_soon_as_a_permit_is_released() throws Exception {
        var bulkhead = new Bulkhead(1, Duration.ofSeconds(5));
        var busy = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            pool.submit(() -> bulkhead.call(() -> {
                busy.countDown();
                release.await();
                return null;
            }));
            assertTrue(busy.await(5, TimeUnit.SECONDS));
            var waiter = pool.submit(() -> bulkhead.call(() -> "got in"));

            release.countDown();

            assertEquals("got in", waiter.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void the_permit_is_released_even_when_the_work_fails() {
        var bulkhead = new Bulkhead(1, Duration.ZERO);

        assertThrows(IllegalStateException.class, () -> bulkhead.call(() -> {
            throw new IllegalStateException("boom");
        }));

        assertEquals(1, bulkhead.available());
    }

    @Test
    void an_interrupted_wait_is_an_overload_and_keeps_the_interrupt_flag() {
        var bulkhead = new Bulkhead(1, Duration.ofSeconds(5));
        Thread.currentThread().interrupt();

        assertThrows(Bulkhead.Overloaded.class, () -> bulkhead.call(() -> "x"));

        assertTrue(Thread.interrupted());
    }
}
