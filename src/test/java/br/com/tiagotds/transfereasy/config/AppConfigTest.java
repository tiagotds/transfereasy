package br.com.tiagotds.transfereasy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AppConfigTest {

    @Test
    void defaults_apply_when_nothing_is_set() {
        var config = AppConfig.fromEnvironment(Map.of());

        assertEquals(8080, config.port());
        assertEquals(32, config.dbPoolSize());
    }

    @Test
    void blank_values_fall_back_to_defaults() {
        assertEquals(8080, AppConfig.fromEnvironment(Map.of("PORT", "  ")).port());
    }

    @Test
    void values_are_read_from_the_environment() {
        var config = AppConfig.fromEnvironment(Map.of("PORT", " 9090 ", "DB_POOL_SIZE", "4"));

        assertEquals(9090, config.port());
        assertEquals(4, config.dbPoolSize());
    }

    @Test
    void non_numeric_or_non_positive_values_fail_fast_naming_the_variable() {
        for (var bad : new String[]{"abc", "0", "-5"}) {
            var e = assertThrows(IllegalArgumentException.class,
                    () -> AppConfig.fromEnvironment(Map.of("PORT", bad)));
            assertEquals("Environment variable PORT must be a positive integer, got: " + bad, e.getMessage());
        }
    }
}
