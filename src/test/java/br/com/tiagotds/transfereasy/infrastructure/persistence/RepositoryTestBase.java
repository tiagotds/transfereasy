package br.com.tiagotds.transfereasy.infrastructure.persistence;

import br.com.tiagotds.transfereasy.infrastructure.config.Config;
import br.com.tiagotds.transfereasy.infrastructure.config.Settings;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/** Each test gets its own in-memory database; repository calls run inside {@link #tx(Supplier)}. */
abstract class RepositoryTestBase {

    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-01-15T10:00:00Z");

    Database database;
    TransactionRunner runner;

    @BeforeEach
    void startDatabase() {
        database = Database.startInMemory("repo-" + UUID.randomUUID(), Settings.from(Config.defaults()).database());
        runner = new TransactionRunner(database);
    }

    @AfterEach
    void stopDatabase() {
        database.close();
    }

    <T> T tx(Supplier<T> work) {
        return runner.inTransaction(work);
    }

    void run(Runnable work) {
        runner.inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
