package br.com.tiagotds.transfereasy.infrastructure.config;

/** Size of a statement page: used when no {@code limit} is given, and the largest {@code limit} accepted. */
public record StatementSettings(int defaultSize, int maxSize) {

    public StatementSettings {
        if (defaultSize > maxSize) {
            throw new IllegalArgumentException(
                    "statement.default-size (" + defaultSize + ") must not exceed statement.max-size (" + maxSize + ")");
        }
    }

    static StatementSettings from(Config config) {
        return new StatementSettings(config.positiveInt("statement.default-size"),
                config.positiveInt("statement.max-size"));
    }
}
