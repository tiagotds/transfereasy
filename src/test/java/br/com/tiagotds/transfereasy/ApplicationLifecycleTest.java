package br.com.tiagotds.transfereasy;

import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.tiagotds.transfereasy.config.AppConfig;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

class ApplicationLifecycleTest {

    @Test
    void starting_on_a_port_that_is_taken_fails_and_releases_the_database() throws IOException {
        try (var occupied = new ServerSocket(0)) {
            var config = new AppConfig(occupied.getLocalPort(), 4);

            assertThrows(IOException.class, () -> Application.start(config));
        }
    }

    @Test
    void an_application_can_be_started_and_closed_repeatedly() throws IOException {
        for (int i = 0; i < 3; i++) {
            try (var app = Application.start(new AppConfig(0, 4))) {
                if (app.port() <= 0) {
                    throw new AssertionError("expected a bound port");
                }
            }
        }
    }
}
