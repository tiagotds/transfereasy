package br.com.tiagotds.transfereasy.application.pipeline;

import static br.com.tiagotds.transfereasy.jooq.Tables.CUSTOMERS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.tiagotds.transfereasy.application.command.CreateCustomer;
import br.com.tiagotds.transfereasy.domain.error.ConcurrentModification;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.CustomerName;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import br.com.tiagotds.transfereasy.infrastructure.config.Config;
import br.com.tiagotds.transfereasy.infrastructure.config.RetrySettings;
import br.com.tiagotds.transfereasy.infrastructure.config.Settings;
import br.com.tiagotds.transfereasy.infrastructure.persistence.Database;
import br.com.tiagotds.transfereasy.infrastructure.persistence.JooqCustomerRepository;
import br.com.tiagotds.transfereasy.infrastructure.persistence.TransactionRunner;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Retry must sit OUTSIDE the transaction: a failed attempt is rolled back, the next one starts clean. */
class RetryAroundTransactionTest {

    @Test
    void each_attempt_runs_in_a_fresh_transaction_and_only_the_successful_one_commits() {
        var settings = Settings.from(Config.defaults().with("db.pool-size", "1"));
        try (var database = Database.startInMemory("retry-" + UUID.randomUUID(), settings.database())) {
            var tx = new TransactionRunner(database);
            var customers = new JooqCustomerRepository();
            var attempts = new AtomicInteger();
            var bus = CommandBus.builder()
                    .use(new RetryMiddleware(new RetrySettings(3, Duration.ZERO, Duration.ZERO), d -> { }, () -> 0))
                    .use(new TransactionMiddleware(tx))
                    .handle(CreateCustomer.class, command -> {
                        var customer = customers.add(command.taxNumber(), command.name(), OffsetDateTime.now());
                        if (attempts.incrementAndGet() < 3) {
                            throw ConcurrentModification.of(AccountNumber.of("x"));
                        }
                        return customer;
                    })
                    .build();

            bus.execute(new CreateCustomer(TaxNumber.of("1"), CustomerName.of("Ada")));

            assertEquals(3, attempts.get());
            assertEquals(1, tx.inReadOnlyTransaction(() -> TransactionRunner.current().fetchCount(CUSTOMERS)),
                    "the two failed attempts left nothing behind (else the unique key would have failed)");
        }
    }
}
