package br.com.tiagotds.transfereasy.application.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.tiagotds.transfereasy.application.command.OpenAccount;
import br.com.tiagotds.transfereasy.domain.error.ConcurrentModification;
import br.com.tiagotds.transfereasy.domain.error.InsufficientFunds;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import br.com.tiagotds.transfereasy.infrastructure.config.RetrySettings;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

class RetryMiddlewareTest {

    private static final OpenAccount COMMAND = new OpenAccount(TaxNumber.of("1"));
    private static final RetrySettings SETTINGS =
            new RetrySettings(4, Duration.ofMillis(10), Duration.ofMillis(25));

    private final List<Duration> sleeps = new ArrayList<>();

    /** Jitter fixed at the top of the window, so delays are deterministic: 10, 20, 25 (capped). */
    private RetryMiddleware retry(RetrySettings settings) {
        return new RetryMiddleware(settings, sleeps::add, () -> 1.0);
    }

    private static Middleware.Next failingTimes(int failures, RuntimeException failure, AtomicInteger calls) {
        return () -> {
            if (calls.incrementAndGet() <= failures) {
                throw failure;
            }
            return Outcome.fresh("done");
        };
    }

    @Test
    void a_retryable_failure_is_retried_until_it_succeeds_with_exponential_capped_backoff() {
        var calls = new AtomicInteger();

        var outcome = retry(SETTINGS).around(COMMAND, CommandContext.NONE,
                failingTimes(3, ConcurrentModification.of(AccountNumber.of("a")), calls));

        assertEquals("done", outcome.value());
        assertEquals(4, calls.get());
        assertEquals(List.of(Duration.ofMillis(10), Duration.ofMillis(20), Duration.ofMillis(25)), sleeps);
    }

    @Test
    void when_attempts_are_exhausted_the_last_conflict_reaches_the_caller() {
        var calls = new AtomicInteger();
        var conflict = ConcurrentModification.of(AccountNumber.of("a"));

        var thrown = assertThrows(ConcurrentModification.class, () -> retry(SETTINGS).around(COMMAND,
                CommandContext.NONE, failingTimes(99, conflict, calls)));

        assertSame(conflict, thrown);
        assertEquals(4, calls.get());
        assertEquals(3, sleeps.size(), "no pause after the final attempt");
    }

    @Test
    void business_refusals_are_never_retried() {
        var calls = new AtomicInteger();

        assertThrows(InsufficientFunds.class, () -> retry(SETTINGS).around(COMMAND, CommandContext.NONE,
                failingTimes(1, new InsufficientFunds(), calls)));

        assertEquals(1, calls.get());
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void database_deadlocks_and_lock_timeouts_are_transient_and_retried() {
        for (var sqlState : List.of("40001", "HYT00")) {
            var calls = new AtomicInteger();
            var failure = new DataAccessException("db", new SQLException("x", sqlState));

            retry(SETTINGS).around(COMMAND, CommandContext.NONE, failingTimes(1, failure, calls));

            assertEquals(2, calls.get(), sqlState);
        }
    }

    @Test
    void a_transient_database_failure_that_outlives_the_retries_becomes_a_conflict() {
        var failure = new DataAccessException("db", new SQLException("deadlock", "40001"));

        var thrown = assertThrows(ConcurrentModification.class, () -> retry(SETTINGS).around(COMMAND,
                CommandContext.NONE, failingTimes(99, failure, new AtomicInteger())));

        assertSame(failure, thrown.getCause());
    }

    @Test
    void other_database_failures_are_not_retried() {
        var calls = new AtomicInteger();
        var failure = new DataAccessException("db", new SQLException("syntax", "42000"));

        assertThrows(DataAccessException.class, () -> retry(SETTINGS).around(COMMAND, CommandContext.NONE,
                failingTimes(1, failure, calls)));
        assertThrows(IllegalStateException.class, () -> retry(SETTINGS).around(COMMAND, CommandContext.NONE,
                failingTimes(1, new IllegalStateException(), new AtomicInteger())));

        assertEquals(1, calls.get());
    }

    @Test
    void full_jitter_draws_each_pause_anywhere_between_zero_and_the_exponential_cap() {
        var calls = new AtomicInteger();
        var middleware = new RetryMiddleware(SETTINGS, sleeps::add, () -> 0.5);

        middleware.around(COMMAND, CommandContext.NONE,
                failingTimes(2, ConcurrentModification.of(AccountNumber.of("a")), calls));

        assertEquals(List.of(Duration.ofMillis(5), Duration.ofMillis(10)), sleeps);
    }

    @Test
    void a_single_attempt_disables_retrying() {
        var calls = new AtomicInteger();

        assertThrows(ConcurrentModification.class, () -> retry(new RetrySettings(1, Duration.ZERO, Duration.ZERO))
                .around(COMMAND, CommandContext.NONE,
                        failingTimes(5, ConcurrentModification.of(AccountNumber.of("a")), calls)));

        assertEquals(1, calls.get());
    }

    @Test
    void an_interrupted_pause_stops_retrying_and_keeps_the_interrupt_flag() {
        var conflict = ConcurrentModification.of(AccountNumber.of("a"));
        var middleware = new RetryMiddleware(SETTINGS, d -> {
            throw new InterruptedException();
        }, () -> 1.0);

        var thrown = assertThrows(ConcurrentModification.class, () -> middleware.around(COMMAND,
                CommandContext.NONE, failingTimes(5, conflict, new AtomicInteger())));

        assertSame(conflict, thrown);
        assertTrue(Thread.interrupted(), "interrupt flag must be restored (and is cleared here)");
    }
}
