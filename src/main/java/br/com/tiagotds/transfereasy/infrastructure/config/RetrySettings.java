package br.com.tiagotds.transfereasy.infrastructure.config;

import java.time.Duration;

/** @param maxAttempts total attempts including the first; {@code 1} disables retrying */
public record RetrySettings(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {

    public RetrySettings {
        if (initialBackoff.compareTo(maxBackoff) > 0) {
            throw new IllegalArgumentException("retry.initial-backoff (" + initialBackoff
                    + ") must not exceed retry.max-backoff (" + maxBackoff + ")");
        }
    }

    static RetrySettings from(Config config) {
        return new RetrySettings(config.positiveInt("retry.max-attempts"), config.duration("retry.initial-backoff"),
                config.duration("retry.max-backoff"));
    }
}
