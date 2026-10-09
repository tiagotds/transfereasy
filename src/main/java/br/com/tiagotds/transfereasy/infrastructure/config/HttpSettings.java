package br.com.tiagotds.transfereasy.infrastructure.config;

import java.time.Duration;

/**
 * @param port           {@code 0} binds an ephemeral port
 * @param maxInFlight    requests processed concurrently (bulkhead size)
 * @param acquireTimeout how long a request waits for a bulkhead slot before being refused
 * @param retryAfter     hint sent to refused clients
 */
public record HttpSettings(int port, int maxBodyBytes, int maxInFlight, Duration acquireTimeout,
                           Duration retryAfter) {

    static HttpSettings from(Config config) {
        return new HttpSettings(config.port("http.port"), config.positiveInt("http.max-body-bytes"),
                config.positiveInt("http.max-in-flight"), config.duration("http.acquire-timeout"),
                config.duration("http.retry-after"));
    }
}
