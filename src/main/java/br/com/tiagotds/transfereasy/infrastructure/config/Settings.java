package br.com.tiagotds.transfereasy.infrastructure.config;

/** Every setting of the application, parsed and validated once at startup. */
public record Settings(HttpSettings http, DatabaseSettings database, StatementSettings statements,
                       MoneySettings money) {

    public static Settings from(Config config) {
        return new Settings(HttpSettings.from(config), DatabaseSettings.from(config),
                StatementSettings.from(config), MoneySettings.from(config));
    }
}
