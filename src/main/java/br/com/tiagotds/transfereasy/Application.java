package br.com.tiagotds.transfereasy;

import br.com.tiagotds.transfereasy.http.ApiRoutes;
import br.com.tiagotds.transfereasy.http.HttpApplication;
import br.com.tiagotds.transfereasy.infrastructure.config.Settings;
import br.com.tiagotds.transfereasy.infrastructure.persistence.Database;
import br.com.tiagotds.transfereasy.infrastructure.persistence.TransactionRunner;
import java.io.IOException;
import java.time.Clock;
import java.util.UUID;

/** Composition root: database, core and HTTP edge, wired by hand (no DI container). */
public final class Application implements AutoCloseable {

    private final Database database;
    private final HttpApplication http;

    private Application(Database database, HttpApplication http) {
        this.database = database;
        this.http = http;
    }

    public static Application start(Settings settings) throws IOException {
        var database = Database.startInMemory("transfereasy-" + UUID.randomUUID(), settings.database());
        try {
            var core = Core.wire(settings, new TransactionRunner(database), Clock.systemUTC(), UUID::randomUUID);
            var http = new HttpApplication(settings.http(), new ApiRoutes(core).build());
            http.start();
            return new Application(database, http);
        } catch (IOException | RuntimeException e) {
            database.close();
            throw e;
        }
    }

    public int port() {
        return http.port();
    }

    @Override
    public void close() {
        http.close();
        database.close();
    }
}
