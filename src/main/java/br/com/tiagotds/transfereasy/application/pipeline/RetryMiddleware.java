package br.com.tiagotds.transfereasy.application.pipeline;

import br.com.tiagotds.transfereasy.application.command.Command;
import br.com.tiagotds.transfereasy.domain.error.ConcurrentModification;
import br.com.tiagotds.transfereasy.domain.error.Retryable;
import br.com.tiagotds.transfereasy.infrastructure.config.RetrySettings;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Re-runs the rest of the chain when it lost a race. Must be registered <em>outside</em>
 * {@link TransactionMiddleware}, so every attempt is a brand new transaction on fresh data.
 *
 * <p>Transient failures are {@link Retryable} domain exceptions (optimistic conflicts) and database errors whose
 * SQLSTATE means "a competitor was in the way": deadlock / serialization failure ({@code 40001}) and lock timeout
 * ({@code HYT00}). Everything else, business refusals included, passes straight through.
 *
 * <p>Pause before attempt {@code n} is drawn uniformly from {@code [0, min(max, initial * 2^(n-1))]}
 * ("full jitter"), which spreads competitors apart instead of making them collide again in lockstep.
 */
public final class RetryMiddleware implements Middleware {

    private static final Logger LOG = Logger.getLogger(RetryMiddleware.class.getName());
    private static final Set<String> TRANSIENT_SQL_STATES = Set.of("40001", "HYT00");

    /** Pauses the current thread; a seam so tests run without real waiting. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final RetrySettings settings;
    private final Sleeper sleeper;
    private final DoubleSupplier jitter;

    /** @param jitter returns a fraction in {@code [0, 1)} of the backoff window to actually wait */
    public RetryMiddleware(RetrySettings settings, Sleeper sleeper, DoubleSupplier jitter) {
        this.settings = settings;
        this.sleeper = sleeper;
        this.jitter = jitter;
    }

    public static RetryMiddleware withJitter(RetrySettings settings) {
        return new RetryMiddleware(settings, d -> Thread.sleep(d), () -> ThreadLocalRandom.current().nextDouble());
    }

    @Override
    public Outcome<?> around(Command<?> command, CommandContext context, Next next) {
        for (int attempt = 1; ; attempt++) {
            try {
                return next.proceed();
            } catch (RuntimeException failure) {
                if (!isTransient(failure)) {
                    throw failure;
                }
                if (attempt >= settings.maxAttempts() || !pause(attempt)) {
                    throw asConflict(failure, command, attempt);
                }
            }
        }
    }

    static boolean isTransient(Throwable failure) {
        for (var t = failure; t != null; t = t.getCause()) {
            if (t instanceof Retryable) {
                return true;
            }
            if (t instanceof SQLException sql && TRANSIENT_SQL_STATES.contains(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    /** @return {@code false} when interrupted: stop retrying, keep the interrupt flag for the caller */
    private boolean pause(int attempt) {
        long windowMillis = Math.min(settings.maxBackoff().toMillis(),
                settings.initialBackoff().toMillis() << Math.min(attempt - 1, 30));
        try {
            sleeper.sleep(Duration.ofMillis(Math.round(windowMillis * jitter.getAsDouble())));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static RuntimeException asConflict(RuntimeException failure, Command<?> command, int attempts) {
        LOG.log(Level.FINE, () -> "Giving up on " + command.getClass().getSimpleName() + " after " + attempts
                + " attempt(s)");
        if (failure instanceof ConcurrentModification) {
            return failure;
        }
        return ConcurrentModification.retriesExhausted(failure);
    }
}
