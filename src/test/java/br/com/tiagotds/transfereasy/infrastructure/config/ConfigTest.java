package br.com.tiagotds.transfereasy.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConfigTest {

    private static Properties system(String key, String value) {
        var properties = new Properties();
        properties.setProperty(key, value);
        return properties;
    }

    @Nested
    class Layering {

        @Test
        void defaults_come_from_application_properties_on_the_classpath() {
            var config = Config.defaults();

            assertEquals(8080, config.port("http.port"));
            assertEquals(32, config.positiveInt("db.pool-size"));
        }

        @Test
        void an_environment_variable_in_upper_snake_case_overrides_the_default() {
            var config = Config.load(Map.of("DB_POOL_SIZE", " 4 "), new Properties());

            assertEquals(4, config.positiveInt("db.pool-size"));
        }

        @Test
        void a_system_property_overrides_the_environment() {
            var config = Config.load(Map.of("DB_POOL_SIZE", "4"), system("db.pool-size", "8"));

            assertEquals(8, config.positiveInt("db.pool-size"));
        }

        @Test
        void blank_overrides_are_ignored() {
            var config = Config.load(Map.of("DB_POOL_SIZE", "  "), system("db.pool-size", ""));

            assertEquals(32, config.positiveInt("db.pool-size"));
        }

        @Test
        void with_returns_a_copy_holding_the_new_value() {
            var original = Config.defaults();

            var changed = original.with("db.pool-size", "2");

            assertEquals(2, changed.positiveInt("db.pool-size"));
            assertEquals(32, original.positiveInt("db.pool-size"));
        }

        @Test
        void the_environment_variable_name_is_derived_from_the_key() {
            assertEquals("RETRY_MAX_ATTEMPTS", Config.environmentName("retry.max-attempts"));
        }
    }

    @Nested
    class Parsing {

        @Test
        void durations_accept_milliseconds_seconds_and_iso_8601() {
            var config = Config.defaults().with("a", "250ms").with("b", "2s").with("c", "PT1M");

            assertEquals(Duration.ofMillis(250), config.duration("a"));
            assertEquals(Duration.ofSeconds(2), config.duration("b"));
            assertEquals(Duration.ofMinutes(1), config.duration("c"));
        }

        @Test
        void decimals_and_enums_are_parsed() {
            var config = Config.defaults().with("d", "10.50").with("e", "seconds");

            assertEquals(new BigDecimal("10.50"), config.positiveDecimal("d"));
            assertEquals(java.util.concurrent.TimeUnit.SECONDS,
                    config.enumeration("e", java.util.concurrent.TimeUnit.class));
        }

        @Test
        void ports_may_be_zero_to_request_an_ephemeral_port() {
            assertEquals(0, Config.defaults().with("p", "0").port("p"));
        }
    }

    @Nested
    class Failures {

        @Test
        void an_invalid_value_fails_fast_naming_the_key_and_the_value() {
            var config = Config.defaults().with("db.pool-size", "abc");

            var e = assertThrows(IllegalArgumentException.class, () -> config.positiveInt("db.pool-size"));

            assertEquals("Invalid configuration 'db.pool-size' = 'abc': must be a positive integer", e.getMessage());
        }

        @Test
        void each_parser_rejects_out_of_range_values() {
            var config = Config.defaults().with("zero", "0").with("neg", "-1").with("big", "70000")
                    .with("bad", "soon").with("e", "nope");

            assertThrows(IllegalArgumentException.class, () -> config.positiveInt("zero"));
            assertThrows(IllegalArgumentException.class, () -> config.port("neg"));
            assertThrows(IllegalArgumentException.class, () -> config.port("big"));
            assertThrows(IllegalArgumentException.class, () -> config.duration("bad"));
            assertThrows(IllegalArgumentException.class, () -> config.duration("neg"));
            assertThrows(IllegalArgumentException.class, () -> config.positiveDecimal("zero"));
            assertThrows(IllegalArgumentException.class,
                    () -> config.enumeration("e", java.util.concurrent.TimeUnit.class));
        }

        @Test
        void a_missing_key_is_reported() {
            var e = assertThrows(IllegalArgumentException.class, () -> Config.defaults().positiveInt("nope"));

            assertEquals("Missing configuration 'nope'", e.getMessage());
        }

        @Test
        void a_missing_defaults_resource_is_reported() {
            assertThrows(IllegalStateException.class, () -> Config.load("/does-not-exist.properties", Map.of(),
                    new Properties()));
        }
    }
}
