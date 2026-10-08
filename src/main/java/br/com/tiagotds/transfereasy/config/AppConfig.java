package br.com.tiagotds.transfereasy.config;

import java.util.Map;

/** Runtime settings read from environment variables, with safe defaults. */
public record AppConfig(int port, int dbPoolSize) {

    public static AppConfig fromEnvironment(Map<String, String> env) {
        return new AppConfig(positive(env, "PORT", 8080), positive(env, "DB_POOL_SIZE", 32));
    }

    private static int positive(Map<String, String> env, String key, int fallback) {
        var raw = env.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // reported below with the offending key
        }
        throw new IllegalArgumentException("Environment variable " + key + " must be a positive integer, got: " + raw);
    }
}
