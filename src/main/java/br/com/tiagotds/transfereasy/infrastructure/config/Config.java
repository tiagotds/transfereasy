package br.com.tiagotds.transfereasy.infrastructure.config;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

/**
 * Layered key/value configuration: {@code application.properties} on the classpath, overridden by environment
 * variables ({@code db.pool-size} &harr; {@code DB_POOL_SIZE}), overridden by system properties.
 *
 * <p>Values are parsed on access by typed getters that fail fast with the offending key and value.
 */
public final class Config {

    static final String DEFAULTS_RESOURCE = "/application.properties";

    private final Map<String, String> values;

    private Config(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    /** Only the classpath defaults: deterministic, used by tests. */
    public static Config defaults() {
        return load(Map.of(), new Properties());
    }

    /** Defaults plus the real process environment and system properties. */
    public static Config fromEnvironment() {
        return load(System.getenv(), System.getProperties());
    }

    static Config load(Map<String, String> environment, Properties systemProperties) {
        return load(DEFAULTS_RESOURCE, environment, systemProperties);
    }

    static Config load(String resource, Map<String, String> environment, Properties systemProperties) {
        var values = new HashMap<String, String>();
        readDefaults(resource).forEach((key, value) -> values.put((String) key, (String) value));
        for (var key : values.keySet()) {
            override(values, key, environment.get(environmentName(key)));
            override(values, key, systemProperties.getProperty(key));
        }
        return new Config(values);
    }

    static String environmentName(String key) {
        return key.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
    }

    /** @return a copy of this configuration with {@code key} set to {@code value}. */
    public Config with(String key, String value) {
        var copy = new HashMap<>(values);
        copy.put(key, value);
        return new Config(copy);
    }

    public int positiveInt(String key) {
        return get(key, "a positive integer", raw -> {
            int value = Integer.parseInt(raw);
            return value > 0 ? value : null;
        });
    }

    /** A TCP port; {@code 0} asks the OS for an ephemeral one. */
    public int port(String key) {
        return get(key, "a port between 0 and 65535", raw -> {
            int value = Integer.parseInt(raw);
            return value >= 0 && value <= 65_535 ? value : null;
        });
    }

    /** Accepts {@code 250ms}, {@code 2s} or an ISO-8601 duration such as {@code PT1M}; never negative. */
    public Duration duration(String key) {
        return get(key, "a non-negative duration (e.g. 250ms, 2s, PT1M)", raw -> {
            Duration value;
            if (raw.endsWith("ms")) {
                value = Duration.ofMillis(Long.parseLong(raw.substring(0, raw.length() - 2)));
            } else if (raw.endsWith("s") && !raw.startsWith("P")) {
                value = Duration.ofSeconds(Long.parseLong(raw.substring(0, raw.length() - 1)));
            } else {
                value = Duration.parse(raw);
            }
            return value.isNegative() ? null : value;
        });
    }

    public BigDecimal positiveDecimal(String key) {
        return get(key, "a positive decimal", raw -> {
            var value = new BigDecimal(raw);
            return value.signum() > 0 ? value : null;
        });
    }

    public <E extends Enum<E>> E enumeration(String key, Class<E> type) {
        return get(key, "one of " + java.util.Arrays.toString(type.getEnumConstants()),
                raw -> Enum.valueOf(type, raw.toUpperCase(Locale.ROOT)));
    }

    /**
     * Generic typed access. The parser returns the value, or {@code null} / throws when the raw text is invalid;
     * either way the failure is reported with the key, the value and what was expected.
     */
    public <T> T get(String key, String expected, Function<String, T> parser) {
        var raw = values.get(key);
        if (raw == null) {
            throw new IllegalArgumentException("Missing configuration '" + key + "'");
        }
        T value;
        try {
            value = parser.apply(raw.trim());
        } catch (RuntimeException e) {
            value = null;
        }
        if (value == null) {
            throw new IllegalArgumentException(
                    "Invalid configuration '" + key + "' = '" + raw + "': must be " + expected);
        }
        return value;
    }

    private static void override(Map<String, String> values, String key, String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            values.put(key, candidate.trim());
        }
    }

    private static Properties readDefaults(String resource) {
        try (var in = Config.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + resource);
            }
            var properties = new Properties();
            properties.load(in);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + resource, e);
        }
    }
}
