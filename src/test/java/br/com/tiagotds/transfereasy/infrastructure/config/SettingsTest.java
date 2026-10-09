package br.com.tiagotds.transfereasy.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SettingsTest {

    @Test
    void defaults_reproduce_the_previous_hard_coded_values() {
        var settings = Settings.from(Config.defaults());

        assertEquals(new HttpSettings(8080, 64 * 1024), settings.http());
        assertEquals(new DatabaseSettings(32, Duration.ofSeconds(10)), settings.database());
        assertEquals(new StatementSettings(100, 1000), settings.statements());
    }

    @Test
    void a_default_statement_size_above_the_maximum_is_rejected() {
        var config = Config.defaults().with("statement.default-size", "2000");

        assertThrows(IllegalArgumentException.class, () -> Settings.from(config));
    }
}
