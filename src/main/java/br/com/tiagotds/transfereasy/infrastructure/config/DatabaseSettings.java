package br.com.tiagotds.transfereasy.infrastructure.config;

import java.time.Duration;

/** @param lockTimeout how long a transaction waits for a row lock before failing */
public record DatabaseSettings(int poolSize, Duration lockTimeout) {

    static DatabaseSettings from(Config config) {
        return new DatabaseSettings(config.positiveInt("db.pool-size"), config.duration("db.lock-timeout"));
    }
}
