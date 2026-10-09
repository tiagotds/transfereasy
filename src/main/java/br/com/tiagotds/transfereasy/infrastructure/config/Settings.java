package br.com.tiagotds.transfereasy.infrastructure.config;

import br.com.tiagotds.transfereasy.infrastructure.persistence.LockingStrategy;

/** Every setting of the application, parsed and validated once at startup. */
public record Settings(HttpSettings http, DatabaseSettings database, StatementSettings statements,
                       MoneySettings money, RetrySettings retry, LockingStrategy locking) {

    public static Settings from(Config config) {
        return new Settings(HttpSettings.from(config), DatabaseSettings.from(config),
                StatementSettings.from(config), MoneySettings.from(config), RetrySettings.from(config),
                config.enumeration("account.locking", LockingStrategy.class));
    }
}
