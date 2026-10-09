package br.com.tiagotds.transfereasy;

import br.com.tiagotds.transfereasy.db.Database;
import br.com.tiagotds.transfereasy.db.TransactionRunner;
import br.com.tiagotds.transfereasy.http.ApiRoutes;
import br.com.tiagotds.transfereasy.http.HttpApplication;
import br.com.tiagotds.transfereasy.infrastructure.config.Settings;
import br.com.tiagotds.transfereasy.repository.AccountRepository;
import br.com.tiagotds.transfereasy.repository.CustomerRepository;
import br.com.tiagotds.transfereasy.repository.LedgerRepository;
import br.com.tiagotds.transfereasy.service.AccountService;
import br.com.tiagotds.transfereasy.service.CustomerService;
import java.io.IOException;
import java.time.Clock;
import java.util.UUID;

/** Composition root: wires the object graph by hand (no DI container). */
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
            var tx = new TransactionRunner(database);
            var customerRepo = new CustomerRepository();
            var accountRepo = new AccountRepository();
            var ledgerRepo = new LedgerRepository();
            var clock = Clock.systemUTC();
            var customers = new CustomerService(tx, customerRepo, accountRepo, clock);
            var accounts = new AccountService(tx, customerRepo, accountRepo, ledgerRepo, clock, UUID::randomUUID,
                    settings.statements(), settings.money().amountPolicy());
            var http = new HttpApplication(settings.http(), new ApiRoutes(customers, accounts).build());
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
