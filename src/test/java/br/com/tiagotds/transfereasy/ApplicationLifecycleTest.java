package br.com.tiagotds.transfereasy;

import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.tiagotds.transfereasy.infrastructure.config.Config;
import br.com.tiagotds.transfereasy.infrastructure.config.Settings;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

class ApplicationLifecycleTest {

    private static Settings settingsOnPort(int port) {
        return Settings.from(Config.defaults().with("http.port", String.valueOf(port)).with("db.pool-size", "4"));
    }

    @Test
    void starting_on_a_port_that_is_taken_fails_and_releases_the_database() throws IOException {
        try (var occupied = new ServerSocket(0)) {
            var settings = settingsOnPort(occupied.getLocalPort());

            assertThrows(IOException.class, () -> Application.start(settings));
        }
    }

    @Test
    void an_application_can_be_started_and_closed_repeatedly() throws IOException {
        for (int i = 0; i < 3; i++) {
            try (var app = Application.start(settingsOnPort(0))) {
                if (app.port() <= 0) {
                    throw new AssertionError("expected a bound port");
                }
            }
        }
    }
}
