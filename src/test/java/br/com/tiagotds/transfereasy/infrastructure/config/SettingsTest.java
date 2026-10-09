package br.com.tiagotds.transfereasy.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.tiagotds.transfereasy.domain.model.Money;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SettingsTest {

    @Test
    void defaults_reproduce_the_previous_hard_coded_values() {
        var settings = Settings.from(Config.defaults());

        assertEquals(new HttpSettings(8080, 64 * 1024), settings.http());
        assertEquals(new DatabaseSettings(32, Duration.ofSeconds(10)), settings.database());
        assertEquals(new StatementSettings(100, 1000), settings.statements());
        assertEquals(Money.of("1000000000000"), settings.money().maxAmount());
        assertEquals(new RetrySettings(10, Duration.ofMillis(2), Duration.ofMillis(100)), settings.retry());
    }

    @Test
    void a_default_statement_size_above_the_maximum_is_rejected() {
        var config = Config.defaults().with("statement.default-size", "2000");

        assertThrows(IllegalArgumentException.class, () -> Settings.from(config));
    }

    @Test
    void retry_backoff_must_not_start_above_its_cap() {
        var config = Config.defaults().with("retry.initial-backoff", "1s").with("retry.max-backoff", "10ms");

        assertThrows(IllegalArgumentException.class, () -> Settings.from(config));
    }

    @Test
    void a_max_amount_finer_than_a_cent_is_rejected() {
        var config = Config.defaults().with("money.max-amount", "10.001");

        assertThrows(IllegalArgumentException.class, () -> Settings.from(config));
    }
}
